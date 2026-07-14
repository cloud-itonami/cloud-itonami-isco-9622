(ns handyman.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [handyman.store :as store]
            [handyman.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Handyman"})
    (store/register-job! st {:job-id "J-1" :client-id "client-1"
                             :name "job-042"
                             :max-handling-weight-kg 40
                             :max-task-duration-hours 4})
    st))

(defn- task-op [weight duration]
  {:op :approve-repair-task :effect :propose :job-id "J-1"
   :handling-weight-kg weight :task-duration-hours duration
   :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-ceilings
  (let [st (fresh-store)
        v (governor/check req {} (task-op 20 2) st)]
    (is (:ok? v))))

(deftest ok-at-exact-ceiling-edges
  (testing "the handling-weight and task-duration ceilings are inclusive"
    (let [st (fresh-store)]
      (is (:ok? (governor/check req {} (task-op 40 4) st))))))

(deftest hard-on-handling-weight-exceeds-ceiling
  (testing "handling weight beyond registered capacity is a strain/injury risk, not effort"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (task-op 80 2) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :handling-weight-exceeds-ceiling (:rule %)) (:violations v))))))

(deftest hard-on-task-duration-exceeds-ceiling
  (testing "the client's site-access grant is time-boxed, not open-ended"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (task-op 20 10) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :task-duration-exceeds-ceiling (:rule %)) (:violations v))))))

(deftest hard-on-unknown-job
  (let [st (fresh-store)
        v (governor/check req {} (assoc (task-op 20 2) :job-id "J-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-job (:rule %)) (:violations v)))))

(deftest hard-on-foreign-job
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (task-op 20 2) st)]
      (is (:hard? v))
      (is (some #(= :job-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (task-op 20 2) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (task-op 20 2) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest always-escalates-electrical-plumbing-access-even-at-high-confidence
  (testing "no electrical/plumbing-system access without the governor gate"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-electrical-plumbing-access :effect :propose
                                    :job-id "J-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-working-at-height-even-at-high-confidence
  (testing "working-at-height tasks require human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-working-at-height :effect :propose
                                    :job-id "J-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (task-op 20 2) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
