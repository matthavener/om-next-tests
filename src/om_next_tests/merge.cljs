(ns om-next-tests.merge
  (:require
   [om.next :as om :refer-macros [defui]]
   [sablono.core :as sab :refer-macros [html]]
   [om-next-tests.basic-parser :as b]
   [cljs.test :refer-macros [is testing async]] 
   )
  (:require-macros
   [devcards.core :as dc :refer [defcard deftest defcard-om-next]]))

(enable-console-print!)

(defui Item
  static om/Ident
  (ident [this {:keys [db/id]}]
    [:db/id id])
  static om/IQuery
  (query [this]
    [:db/id :item/name :item/description])
  Object
  (render [this]
    (let [{:keys [db/id item/name item/description]} (om/props this)]
      (html [:li.list-group-item
             [:h3 "(" id ")" name [:small description]]]))))

(def item (om/factory Item {:keyfn :db/id}))

(defui ItemList
  static om/IQuery
  (query [this]
    [{:items (om/get-query Item)}
     {:items2 (om/get-query Item)}
     {:items3 [{:items (om/get-query Item)}]}
     ])
  Object
  (render [this]
    (let [{:keys [items items2 items3]} (om/props this)]
      (println (om/props this))
      (html [:div
             [:h1 "Item List 1"]
             [:ul
              (map item items)]
             [:h1 "Item List 2"]
             [:ul
              (map item items2)]

             [:h1 "Item List 3"]
             [:ul
              (map item (:items items3))]]))))



(defonce item-reconciler
  (om/reconciler {:state {:items [{:db/id 1
                                   :item/name "item1"
                                   :item/description "A nice item"}
                                  {:db/id 2
                                   :item/name "item2"
                                   :item/description "Another nice item"}]}
                  :normalize true
                  :id-key :db/id
                  :parser (om/parser {:read b/basic-reader})}))

(defcard-om-next item-list-card
  ItemList
  item-reconciler)

(dc/deftest single-component-test
  "This is a simple case to verify db normalization and parsing is working."
  (testing "Simple Component"
    (let [st (deref item-reconciler)]
      (testing "reconciler normalized the data"
        (is (= "item1" (get-in st [:db/id 1 :item/name])))
        (is (= [:db/id 1] (-> st
                              :items
                              first))))))
  (testing "Getting component state"
    (let [c (om/class->any item-reconciler ItemList)
          props (om/props c)]
      (is (= 2 (count (:items props)))))))


(defonce item-reconciler-merge
  (om/reconciler {:state {:items [{:db/id 1
                                   :item/name "item1"
                                   :item/description "A nice item"}
                                  {:db/id 2
                                   :item/name "item2"
                                   :item/description "Another nice item"}]}
                  :normalize true
                  :id-key :db/id
                  :parser (om/parser {:read b/basic-reader})}))

(defcard-om-next item-list-card
  ItemList
  item-reconciler-merge)

(dc/deftest merge-data-test
  "This is another case using a separate reconciler to not affect the above simple sanity check case. If merged data is a superset of existing data (and contains that data), then the merge works fine.  If the merged data is a different set of
 data, the existing data is replaced.  This is surprising behaviour."
  (testing "Merging Data"
    (testing "merging in state (same db/ids, different list)"
      (let [next-state {:items2 [{:db/id 1
                                  :item/name "item1"
                                  :item/description "A nice item"}
                                 {:db/id 2
                                  :item/name "item2"
                                  :item/description "Another nice item"}
                                 {:db/id 3
                                  :item/name "item3"
                                  :item/description "New item"}]}
            st (om/merge! item-reconciler-merge next-state)]
        (is (= "item1" (get-in st [:db/id 1 :item/name])))
        (is (= "item3" (get-in st [:db/id 3 :item/name])))))
    (testing "merging in more state state (different db/ids, different list)"
      (let [next-state {:items3 {:items [{:db/id 5
                                          :item/name "item5"
                                          :item/description "A nice item"}
                                         {:db/id 4
                                          :item/name "item4"
                                          :item/description "Another nice item"}]}}
            st (om/merge! item-reconciler-merge next-state)]
        (is (= "item1" (get-in st [:db/id 1 :item/name])))
        (is (= "item3" (get-in st [:db/id 3 :item/name])))
        (is (= "item4" (get-in st [:db/id 4 :item/name])))
        (testing "The prior refs are still in the item lists, and in the :db/id table"
          (is (= [[:db/id 1] [:db/id 2]] (get-in st [:items])))
          (is (= "item1" (get-in st [:db/id 1 :item/name]))))))
    
    )
  (testing "Getting component state"
    (let [c (om/class->any item-reconciler-merge ItemList)
          props (om/props c)]
      (is (= 2 (count (:items props)))))))
