(defproject bulk-image-generator "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [clj-http "3.12.3"]
                 [com.taoensso/carmine "3.2.0"]
                 [cheshire "5.11.0"]
                 [ring "1.9.5"]
                 [metosin/reitit "0.7.0-alpha5"]
                 [metosin/muuntaja "0.6.8"]
                 [hiccup "2.0.0-RC1"]
                 [ring/ring-jetty-adapter "1.9.5"]
                 ]
  :repl-options {:init-ns bulk-image-generator.core})
