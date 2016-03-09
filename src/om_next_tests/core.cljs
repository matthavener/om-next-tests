(ns om-next-tests.core
  (:require
   [om.next :as om :refer-macros [defui]]
   [sablono.core :as sab :refer-macros [html]]
   [om-next-tests.merge]
   [om-next-tests.tree-links]
   [om-next-tests.parser]
   [devcards.core :as dc])
  (:require-macros
   [devcards.core :as dc :refer [defcard deftest]]))

(enable-console-print!)

(defn main []
  (devcards.core/start-devcard-ui!)
  )

(main)

;; remember to run lein figwheel and then browse to
;; http://localhost:3449/cards.html

