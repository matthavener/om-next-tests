(ns om-next-tests.basic-parser
  (:require
   [om.next :as om]
   [cljs.pprint :refer [pprint]]))

(defn tables
  [db]
  ;; The initial normalization of state doesn't pull in the tables (bug? calls tree->db with merge-idents false)
  (or (:om.next/tables db) #{:db/id}))

(defn get-db
  [state all-state k]
  #_(println "get-db:" k " -> " (find state k) " tables " (:om.next/tables all-state))
  (if-let [[_ v] (if (and (vector? k)
                          ((tables all-state) (first k)))
                   [(first k) (get-in all-state k)]
                   (find state k))]
    (if (and (vector? v)
             ((tables all-state) (first v)))
      (get-in all-state v)
      v)))

(defn read-db
  [st db-path ast]
  (let [k (:key ast)
        rst (if db-path
             (get-in st db-path)
             st)]
    (case (:type ast)
      :prop
      (get-db rst st k)

      :query
      (select-keys rst (:query ast))

      :join
      (let [tr (om/db->tree (:query ast) (get rst k) st)]
        #_(println "join tree:" tr)
        #_(println "query: " ast)
        #_(println "rst: " rst)
        #_(println "st: " st)
        tr)

      (do
       (println "unexpected ast type: " (:type ast))
       :unsupported))))

(defn simple-reader
  [{:keys [state ast db-path]} k params]
  (let [v (read-db @state db-path ast)]
    #_(println "simple-reader> " (:key ast) ":" db-path "->" ast "=" v)
    {:value v}))

(defn check-missing-keys
  "Return a new ast with missing keys"
  [target state all-state {:keys [children dispatch-key type params] :as ast} parent-elided?]
  #_(println "check-missing-keys: " dispatch-key "(" parent-elided? ") params:" params "ast:" ast " children: " children)
  (if-let [v (or (get-db state all-state (:key ast)) (when (and (get params :parser/elide) (= type :join)) {}))] 
    (let [#_ (println "got value: " v)
          missing-children (into [] (comp
                                     (map #(check-missing-keys target v all-state % (get params :parser/elide)))
                                     (remove nil?)) children)]
      (when-not (empty? missing-children)
       #_(println "missing children: " dispatch-key " - " missing-children)
       (-> ast
           (assoc :query (mapv om/ast->query missing-children))
           (assoc :children missing-children))))
    (when (= (:parser/target params) target)
     #_(println "missing key: " dispatch-key)
     (pprint ast)
     (pprint (om/ast->query (assoc ast :key :requests)))
     (let [ast' (cond-> ast (om/ident? (:key ast)) (assoc :key (first (:key ast))))]
       (assoc ast' :query-root parent-elided? :query (mapv om/ast->query (:children ast'))))
     )))

(defn basic-reader
  [{:keys [state parser ast query calls db-path target] :as env} k params]
  (when calls
    (swap! calls conj {:env (dissoc env :state :parser :calls)
                       :key k
                       :params params}))

  #_(println "basic-reader" db-path ":" k ":" target " -> " ast #_(om/ast->query ast))

  (if target
    (let [st @state
          ast' (check-missing-keys target st st ast false)]
      (println "remote query" ast')
      {target ast'})
    (simple-reader env k params)))

