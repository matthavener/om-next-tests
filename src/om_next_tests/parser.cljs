(ns om-next-tests.parser
  (:require
   [sablono.core :refer-macros [html]]
   [devcards.core :as dc :refer-macros [defcard defcard-doc defcard-om-next]]
   [om.next :as om :refer-macros [defui]]
   [cljs.core.async :as async]
   [om-next-tests.basic-parser :as b]
   [clojure.walk :as walk]
   [cljs.test :refer-macros [is testing async]])
  (:require-macros
   [cljs.core.async.macros :refer [go]]))


(dc/deftest ui-parser-card
  "This simple parser should return some items"

  (let [p (om/parser {:read b/simple-reader})]
    (testing "simple properties"
      (let [r (p {:state (atom {:a 42 :b "hello" :c [1 2 3]})}
                 [:a :b :c])]
        (is (= {:a 42 :b "hello" :c [1 2 3]} r))))
    (testing "join query"
      (let [r (p {:state (atom {:a {:b {:c 1 :d 2}}})}
                 [{:a [{:b [:c :d]}]}])]
        (is (= {:a {:b {:c 1 :d 2}}} r)))))

  (let [p (om/parser {:read b/basic-reader})
        calls (atom [])]

    (testing "singleton ident"
      (let [r (p {:state (atom {:skip {}})}
                 [(list {:skip [(list {[:requests '_] ['*]} {:parser/target :remote})]} {:parser/elide true})] :remote)]
        (is (= r [(list {:skip [(list {:requests ['*]} {:parser/target :remote})]} {:parser/elide true})])))
      (let [r (p {:state (atom {:skip {}})}
                 [(list {:skip [(list {[:requests '_] ['*]} {:parser/target :remote}) (list {[:shipments '_] ['*]} {:parser/target :remote})]} {:parser/elide true})] :remote)]
        (is (= r [(list {:skip [(list {:requests ['*]} {:parser/target :remote}) (list {:shipments ['*]} {:parser/target :remote})]} {:parser/elide true})])))
      )
    (testing "recursive reader"
      (let [r (p {:state (atom {:outer :a-value
                                :skip {:real {:a 1 :b 2 :c 3}
                                       :inner-value :a-inner-value }})
                  :calls calls}
                 [:outer (list {:skip [{:real [:a :b :c]} :inner-value]} {:parser/elide true})])]
        (is (= {:outer :a-value
                :skip {:real {:a 1 :b 2 :c 3}
                       :inner-value :a-inner-value }} r))
        #_(cljs.pprint/pprint @calls)))

    (testing "normalized state"
      (let [r (p {:state (atom {:skip {:a {:items [[:id 1] [:id 2] [:id 3]]}}
                                :id {1 {:name "phone"}
                                     2 {:name "box"}
                                     3 {:name "pen"}}
                                :om.next/tables #{:id}})}
                 [(list {:skip [{:a [{:items [:name]}]}]} {:parser/elide true})])]
        (is (= r {:skip
                  {:a {:items [{:name "phone"}
                               {:name "box"}
                               {:name "pen"}]}}})))
      (testing "reading by ident"
        (let [r (p {:state (atom {:skip {:a {:items [[:id 1] [:id 2] [:id 3]]}}
                                  :focus [:id 1]
                                  :id {1 {:name "phone"}
                                       2 {:name "box"}
                                       3 {:name "pen"}}
                                  :om.next/tables #{:id}})}
                   [[:id 2] :focus])]
          (is (= (get r :focus) {:name "phone"}))
          (is (= (get r [:id 2]) {:name "box"})))))

    (testing "remote queries work"
      (let [r (p {:state (atom {:skip {:a {:items []}}})}
                 [(list {:r-data [:foo :bar]} {:parser/target :remote})] :remote)]
        (is (= r [(list {:r-data [:foo :bar]} {:parser/target :remote})])))
      (let [r (p {:state (atom {:skip {:a {:items []}}
                                :r-data {:foo 42 :bar 1}})}
                 [(list {:r-data [:foo :bar]} {:parser/target :remote})] :remote)]
        (is (= r [])))

      (testing "remote queries skip elided roots"
        (let [r (p {:state (atom {:skip {:a {:items {:name "bob"}}}})}
                   [(list {:skip [{:a [{:items [:name]}]} (list {:r-data [:foo :bar]} {:parser/target :remote})]} {:parser/elide true})] :remote)
              _ (println "elided return:" r)
              {:keys [query rewrite]} (om/process-roots r)]
          (is (= {:r-data [:foo :bar]} (ffirst query)))
          (is (= {:skip {:r-data {:foo 42 :bar 1}}} (rewrite {:r-data {:foo 42 :bar 1}})))
          ))

      (testing "remote queryies with normalized data work and don't return a query unnecessarily"
        (let [s (atom {:skip {:a {:items {:name "bob"}}
                                         :r-data [:db/id 1]}
                                  :db/id {1 {:a 42 :b 10}}
                                  :om.next/tables #{:db/id}})
              r (p {:state s}
                   [(list {:skip [{:r-data [:a :b]}]} {:parser/elide true})]
                   :remote)
              q (p {:state s}
                   [(list {:skip [{:r-data [:a :b]}]} {:parser/elide true})])]
          (is (= [] r))
          (is (= q {:skip {:r-data {:a 42 :b 10}}})))))

      (testing "doubly nested elided works for remote"
         (let [r (p {:state (atom {})} [(list {:skip [(list {:skip2 [(list {:r-data ['*]} {:parser/target :remote})]} {:parser/elide true})]} {:parser/elide true})] :remote)
               {:keys [query rewrite]} (om/process-roots r)]
           (is (= query [(list {:r-data ['*]} {:parser/target :remote})]))
           ))

      (testing "missing elided is added automatically for join"
         (let [r (p {:state (atom {})} [(list {:skip [(list {:r-data [:name]} {:parser/target :remote})]} {:parser/elide true})] :remote)]
           (is (= r [(list {:skip [(list {:r-data [:name]} {:parser/target :remote})]} {:parser/elide true})]))
           ))

      (testing "missing elided doesnt explode for prop"
         (let [r (p {:state (atom {})} [(list {:ui-skip [:local-ui-state]} {:parser/elide true})])]
           (is (= nil (get-in r [:ui-skip :local-ui-state])))
           ))
      )

  (testing "deep rooted remote query"
    (let [q [{:skip [{:r-data [:foo :bar]}]}]
          qr (om/ast->query (walk/postwalk
                             (fn [f]
                               (if (and (map? f) (= (:key f) :r-data))
                                 (assoc f :query-root true)
                                 f))
                             (om/query->ast q)))
          {:keys [query rewrite]} (om/process-roots qr)]
      (is (= [{:r-data [:foo :bar]}] query))
      (is (= {:skip {:r-data {:foo 42 :bar 1}}} (rewrite {:r-data {:foo 42 :bar 1}}))))))
