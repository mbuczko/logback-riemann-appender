(set-env!
 :source-paths #{"src/java"}
 :dependencies '[[org.clojure/clojure "1.8.0" :scope "provided"]
                 [adzerk/bootlaces "0.1.13" :scope "test"]
                 [ch.qos.logback/logback-classic "1.2.1"]
                 [com.aphyr/riemann-java-client "0.4.1" :exclusions [org.slf4j/*]]])

;; to check the newest versions:
;; boot -d boot-deps ancient

(def +version+ "0.4.0")

(require '[adzerk.bootlaces :refer [bootlaces! build-jar push-release]])

(bootlaces! +version+ :dont-modify-paths? true)

(task-options! pom {:project 'defunkt/logback-riemann-appender
                    :version +version+
                    :description "Logback appender for riemann."
                    :url "https://github.com/mbuczko/logback-riemann-appender"
                    :scm {:url "https://github.com/mbuczko/logback-riemann-appender"}
                    :license {"name" "Eclipse Public License"
                              "url" "http://www.eclipse.org/legal/epl-v10.html"}})
