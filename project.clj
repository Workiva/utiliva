(defproject com.workiva/utiliva "0.2.0"
  :description "Utilia Workivinarum"
  :url "https://github.com/Workiva/utiliva"

  :license {:name "Eclipse Public License 1.0"}

  :plugins [[lein-shell "0.5.0"]
            [lein-codox "0.10.3"]
            [lein-cljfmt "0.6.4"]]

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.cache "0.6.5"]
                 [com.workiva/tesserae "1.0.0"]
                 [backtick "0.3.4"]]

  :deploy-repositories {"clojars"
                        {:url "https://repo.clojars.org"
                         :sign-releases false}}

  :source-paths      ["src"]
  :test-paths        ["test"]

  :global-vars {*warn-on-reflection* true}

  :aliases {"docs" ["do" "clean-docs," "with-profile" "docs" "codox"]
            "clean-docs" ["shell" "rm" "-rf" "./documentation"]}

  :codox {:metadata {:doc/format :markdown}
          :themes [:rdash]
          :output-path "documentation"}

  :profiles {:dev [{:dependencies [[criterium "0.4.3"]]}]
             :docs {:dependencies [[codox-theme-rdash "0.1.2"]]}})
