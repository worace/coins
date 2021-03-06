(defproject block-chain "0.2.0"
  :description "ClarkeCoin: An Educational Cryptocurrency"
  :url "http://clarkecoin.org"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main block-chain.core
  :plugins [[lein-environ "1.0.3"]]
  :uberjar-name "clarke-coin-node.jar"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [org.clojure/tools.nrepl "0.2.11"]
                 [org.clojure/tools.cli "0.3.5"]
                 [clj-http "2.1.0"]
                 [cheshire "5.5.0"]
                 [compojure "1.5.0"]
                 [metosin/compojure-api "1.0.2"]
                 [ring/ring-core "1.4.0"]
                 [http-kit "2.1.18"]
                 [org.clojure/tools.logging "0.3.1"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                  javax.jms/jms
                                                  com.sun.jdmk/jmxtools
                                                  com.sun.jmx/jmxri]]
                 [environ "1.0.2"]
                 [org.clojure/core.async "0.2.374"]
                 [pandect "0.5.4"]
                 [byte-streams "0.2.2"]
                 [factual/clj-leveldb "0.1.1"]]
  :profiles {:uberjar {:aot :all}})
