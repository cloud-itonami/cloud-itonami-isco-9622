(ns handyman.advisor
  "HandymanAdvisor — the advisor named in this repository's README,
  proposing a handyman operation (approve a repair task, approve
  electrical/plumbing access, approve working-at-height) from a
  service request, task scope and site access. Swappable mock/llm;
  the advisor ONLY proposes — `handyman.governor` checks the
  handling-weight and task-duration ceilings independently and always
  escalates electrical/plumbing-access and working-at-height
  decisions. Modeled on cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-repair-task|:approve-electrical-plumbing-access|:approve-working-at-height
               :effect :propose :job-id str :handling-weight-kg number
               :task-duration-hours number :stake kw :confidence n
               :rationale str}"
  ;; clojure.edn, not clojure.core/read-string: this parses untrusted
  ;; advisor output, and the core reader executes #=(...) at read time.
  (:require [clojure.edn :as edn]))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake job-id handling-weight-kg task-duration-hours] :as request}]
  {:op op
   :effect :propose
   :job-id job-id
   :handling-weight-kg handling-weight-kg
   :task-duration-hours task-duration-hours
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a handyman-practice advisor. Given a request, propose an
   :op, the :job-id, :handling-weight-kg and :task-duration-hours, an
   honest :confidence and a :stake. Never call an over-capacity
   handling weight or an over-time-box task duration conforming — the
   governor checks both against the registered job record. Electrical/
   plumbing-access and working-at-height decisions always require
   human sign-off regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
