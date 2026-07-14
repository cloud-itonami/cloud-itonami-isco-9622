(ns handyman.store
  "SSoT for the ISCO-08 9622 independent handyman practice actor
  (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors section;
  README's 'Robotics premise' — a general repair-support robot
  performs material handling, minor assembly and cleanup tasks under
  this advisor/governor pair, which never dispatches hardware
  itself). Modeled on cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client — a registered organization (:client-id, :name)
    job    — a registered service job {:job-id :client-id :name
             :max-handling-weight-kg number :max-task-duration-hours
             number}. `:max-handling-weight-kg` is the registered
             material-handling capacity a proposed handling weight
             must not exceed — handling weight beyond registered
             capacity is a strain/injury risk, not effort;
             `:max-task-duration-hours` is the registered site-access
             time-box a proposed task duration must not exceed — the
             client's site-access grant is time-boxed, not open-ended.
    record — a committed operating record (approved repair task) —
             written ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (job [s job-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-job! [s j])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (job [_ job-id] (get-in @a [:jobs job-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-job! [s j]
    (swap! a assoc-in [:jobs (:job-id j)] j) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :jobs {} :records [] :ledger []}
                                   seed)))))
