(ns handyman.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [handyman.actor :as actor]
            [handyman.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Handyman"})
    (store/register-job! st {:job-id "J-1" :client-id "client-1"
                             :name "job-042"
                             :max-handling-weight-kg 40
                             :max-task-duration-hours 4})
    st))

(deftest commits-a-within-ceiling-repair-task
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-repair-task :stake :low
                 :job-id "J-1" :handling-weight-kg 20 :task-duration-hours 2}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-ceiling-handling-weight-task
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-repair-task :stake :low
                 :job-id "J-1" :handling-weight-kg 80 :task-duration-hours 2}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-approves-working-at-height-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-working-at-height :stake :low
                 :job-id "J-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
