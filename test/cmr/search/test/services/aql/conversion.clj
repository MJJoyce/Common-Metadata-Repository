(ns cmr.search.test.services.aql.conversion
  (:require [clojure.test :refer :all]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.common.date-time-parser :as dt-parser]
            [cmr.search.services.aql.conversion :as a]
            [cmr.search.models.query :as q]))

(defn- aql-string-elem->condition
  [aql-snippet]
  (let [aql (format "<query><dataCenterId>%s</dataCenterId></query>" aql-snippet)
        xml-struct (x/parse-str aql)]
    (a/element->condition :collection (cx/element-at-path xml-struct [:dataCenterId]))))

(deftest aql-string-conversion-test
  (testing "string aql"
    (are [cond-args aql-snippet]
         (= (apply q/string-condition :provider-id cond-args)
            (aql-string-elem->condition aql-snippet))

         ;; string value
         ["PROV1"] "<value>PROV1</value>"

         ;; string value with caseInsensitive attribute
         ["PROV1"] "<value caseInsensitive=\"Y\">PROV1</value>"
         ["PROV1"] "<value caseInsensitive=\"y\">PROV1</value>"
         ["PROV1" true false] "<value caseInsensitive=\"N\">PROV1</value>"
         ["PROV1" true false] "<value caseInsensitive=\"n\">PROV1</value>"

         ;; textPattern
         ["PROV*" false true] "<textPattern>PROV*</textPattern>"
         ["P*" false true] "<textPattern>P%</textPattern>"
         ["*1" false true] "<textPattern>%1</textPattern>"
         ["?1" false true] "<textPattern>_1</textPattern>"
         ["PROV?" false true] "<textPattern>PROV_</textPattern>"
         ["P\\%\\_R*V?" false true] "<textPattern>P\\%\\_R%V_</textPattern>"

         ;; textPattern with caseInsensitive attribute
         ["PROV?" false true] "<textPattern caseInsensitive=\"Y\">PROV?</textPattern>"
         ["PROV?" false true] "<textPattern caseInsensitive=\"y\">PROV?</textPattern>"
         ["PROV?" true true] "<textPattern caseInsensitive=\"N\">PROV?</textPattern>"
         ["PROV?" true true] "<textPattern caseInsensitive=\"n\">PROV?</textPattern>")

    (are [condition aql-snippet]
         (= condition
            (aql-string-elem->condition aql-snippet))

         ;; list
         (q/string-condition :provider-id "PROV1") "<list><value>PROV1</value></list>"

         (q/or-conds[(q/string-condition :provider-id "PROV1")
                     (q/string-condition :provider-id "PROV2")])
         "<list><value>PROV1</value><value>PROV2</value></list>"

         ;; list with caseInsensitive attribute
         (q/string-condition :provider-id "PROV1" true false)
         "<list><value caseInsensitive=\"N\">PROV1</value></list>"

         (q/or-conds[(q/string-condition :provider-id "PROV1" true false)
                     (q/string-condition :provider-id "PROV2" false false)])
         "<list><value caseInsensitive=\"N\">PROV1</value><value caseInsensitive=\"y\">PROV2</value></list>"

         ;; patternList
         (q/string-condition :provider-id "PROV1") "<patternList><value>PROV1</value></patternList>"
         (q/string-condition :provider-id "PROV*" false true)
         "<patternList><textPattern>PROV%</textPattern></patternList>"

         (q/or-conds[(q/string-condition :provider-id "PROV1")
                     (q/string-condition :provider-id "PROV2")])
         "<patternList><value>PROV1</value><value>PROV2</value></patternList>"

         (q/or-conds[(q/string-condition :provider-id "PROV1")
                     (q/string-condition :provider-id "PROV?" false true)])
         "<patternList><value>PROV1</value><textPattern>PROV_</textPattern></patternList>"

         ;; patternList with caseInsensitive attribute
         (q/string-condition :provider-id "PROV1" true false)
         "<patternList><value caseInsensitive=\"N\">PROV1</value></patternList>"

         (q/or-conds[(q/string-condition :provider-id "PROV1" true false)
                     (q/string-condition :provider-id "PROV2" false false)])
         "<patternList><value caseInsensitive=\"N\">PROV1</value><value caseInsensitive=\"y\">PROV2</value></patternList>"

         (q/or-conds[(q/string-condition :provider-id "PROV1" true true)
                     (q/string-condition :provider-id "PROV2" false false)])
         "<patternList><textPattern caseInsensitive=\"N\">PROV1</textPattern><value caseInsensitive=\"y\">PROV2</value></patternList>")))


(defn- aql-date-range-elem->condition
  [aql-snippet]
  (let [aql (str "<query><dataCenterId><all/></dataCenterId><where><collectionCondition>"
                 (format "<ECHOLastUpdate>%s</ECHOLastUpdate></collectionCondition></where></query>"
                         aql-snippet))
        xml-struct (x/parse-str aql)]
    (a/element->condition :collection (cx/element-at-path xml-struct [:where :collectionCondition :ECHOLastUpdate]))))

(deftest aql-date-range-conversion-test
  (testing "date-range aql"
    (are [start-date stop-date aql-snippet]
         (= (q/map->DateRangeCondition
              {:field :updated-since
               :start-date (when start-date (dt-parser/parse-datetime start-date))
               :end-date (when stop-date (dt-parser/parse-datetime stop-date))})
            (aql-date-range-elem->condition aql-snippet))

         "2001-12-03T01:02:03Z" nil
         "<startDate> <Date YYYY=\"2001\" MM=\"12\" DD=\"03\" HH=\"01\" MI=\"02\" SS=\"03\"/> </startDate>"

         nil "2011-02-12T01:02:03Z"
         "<stopDate> <Date YYYY=\"2011\" MM=\"02\" DD=\"12\" HH=\"01\" MI=\"02\" SS=\"03\"/> </stopDate>"

         "2001-12-03T01:02:03Z" "2011-02-12T01:02:03Z"
         "<startDate> <Date YYYY=\"2001\" MM=\"12\" DD=\"03\" HH=\"01\" MI=\"02\" SS=\"03\"/> </startDate>
         <stopDate> <Date YYYY=\"2011\" MM=\"02\" DD=\"12\" HH=\"01\" MI=\"02\" SS=\"03\"/> </stopDate>")))

(defn- aql-boolean-elem->condition
  [aql-snippet]
  (let [aql (str "<query><dataCenterId><all/></dataCenterId><where><collectionCondition>"
                 (format "%s</collectionCondition></where></query>"
                         aql-snippet))
        xml-struct (x/parse-str aql)]
    (a/element->condition :collection (cx/element-at-path xml-struct [:where :collectionCondition :onlineOnly]))))

(deftest aql-boolean-conversion-test
  (testing "boolean aql"
    (are [value aql-snippet]
         (= (q/map->BooleanCondition {:field :downloadable
                                      :value value})
            (aql-boolean-elem->condition aql-snippet))

         true "<onlineOnly value=\"Y\" />"
         true "<onlineOnly />")))
