(ns handyman.governor
  "HandymanGovernor — the independent safety/traceability layer
  named in this repository's README/business-model.md, gating the
  robot-dispensed physical work (material handling, minor assembly,
  cleanup) an advisor may propose. The governor never dispatches
  hardware itself. Modeled on cloud-itonami-isco-4311's
  bookkeeping.governor. Task twist: a proposed handling weight is an
  arithmetic ceiling against the registered material-handling
  capacity — handling weight beyond registered capacity is a
  strain/injury risk, not effort — and a proposed task duration is an
  arithmetic ceiling against the registered site-access time-box — the
  client's site-access grant is time-boxed, not open-ended.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance     — the organization must be registered.
    2. no-actuation          — proposal :effect must be :propose (the
                               governor never dispatches hardware; it
                               only gates what the robot may execute).
    3. job basis             — a task approval must cite a REGISTERED
                               job belonging to this client.
    4. handling-weight ceiling — the proposed handling weight must not
                               exceed the job's registered
                               :max-handling-weight-kg (a strain/
                               injury risk, not effort).
    5. task-duration ceiling — the proposed task duration must not
                               exceed the job's registered
                               :max-task-duration-hours (site access is
                               time-boxed, not open-ended).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    6. :op :approve-electrical-plumbing-access (no electrical/plumbing-
                               system access without the governor
                               gate).
    7. :op :approve-working-at-height (working-at-height tasks require
                               human sign-off).
    8. low confidence (< `confidence-floor`)."
  (:require [handyman.store :as store]))

(def confidence-floor 0.6)

(def ^:private always-escalate-ops #{:approve-electrical-plumbing-access
                                     :approve-working-at-height})

(defn- hard-violations [{:keys [request proposal]} client-record j]
  (let [{:keys [op handling-weight-kg task-duration-hours]} proposal
        task? (= :approve-repair-task op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor はハードウェアを直接起動しない）"})

      (and task? (nil? j))
      (conj {:rule :unknown-job :detail "未登録 job への作業承認は不可"})

      (and task? j (not= (:client-id j) (:client-id request)))
      (conj {:rule :job-wrong-client :detail "job が別 client のもの"})

      (and task? j (number? handling-weight-kg)
           (> handling-weight-kg (:max-handling-weight-kg j)))
      (conj {:rule :handling-weight-exceeds-ceiling
             :detail (str "取扱重量 " handling-weight-kg "kg > 登録済み上限 "
                          (:max-handling-weight-kg j) "kg（登録済み容量超過は挫傷/負傷リスクであって根性ではない）")})

      (and task? j (number? task-duration-hours)
           (> task-duration-hours (:max-task-duration-hours j)))
      (conj {:rule :task-duration-exceeds-ceiling
             :detail (str "作業時間 " task-duration-hours "h > 登録済み現場アクセス上限 "
                          (:max-task-duration-hours j) "h（現場アクセス許諾は時間で区切られており無制限ではない）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `handyman.store/Store`. Pure — never mutates
  the store, never dispatches the robot."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        j (some->> (:job-id proposal) (store/job store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record j)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
