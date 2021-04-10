(ns babashka.bb-edn-test
  (:require
   [babashka.fs :as fs]
   [babashka.test-utils :as test-utils]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]))

(defn bb [& args]
  (edn/read-string
   {:readers *data-readers*
    :eof nil}
   (apply test-utils/bb nil (map str args))))

(deftest doc-test
  (test-utils/with-config {:paths ["test-resources/task_scripts"]}
    (is (str/includes? (apply test-utils/bb nil
                              (map str ["doc" "tasks"]))
                       "This is task ns docstring."))
    (is (str/includes? (apply test-utils/bb nil
                              (map str ["doc" "tasks/foo"]))
                       "Foo docstring"))
    (is (str/includes? (apply test-utils/bb nil
                              (map str ["doc" "tasks/-main"]))
                       "Main docstring"))))

(deftest deps-test
  (test-utils/with-config '{:deps {medley/medley {:mvn/version "1.3.0"}}}
    (is (= '{1 {:id 1}, 2 {:id 2}}
           (bb "-e" "(require 'medley.core)" "-e" "(medley.core/index-by :id [{:id 1} {:id 2}])"))))
  (testing "--classpath option overrides bb.edn"
    (test-utils/with-config '{:deps {medley/medley {:mvn/version "1.3.0"}}}
      (is (= "src"
             (bb "-cp" "src" "-e" "(babashka.classpath/get-classpath)"))))))

(deftest task-test
  (test-utils/with-config '{:tasks {foo (+ 1 2 3)}}
    (is (= 6 (bb "foo"))))
  (let [tmp-dir (fs/create-temp-dir)
        out (str (fs/file tmp-dir "out.txt"))]
    (test-utils/with-config {:tasks {'foo (list 'shell {:out out}
                                                "echo hello")}}
      (bb "foo")
      (is (= "hello\n" (slurp out))))
    (test-utils/with-config {:tasks {'quux (list 'spit out "quux\n")
                                     'baz (list 'spit out "baz\n" :append true)
                                     'bar {:depends ['baz]
                                           :task (list 'spit out "bar\n" :append true)}
                                     'foo {:depends ['quux 'bar 'baz]
                                           :task (list 'spit out "foo\n" :append true)}}}
      (bb "foo")
      (is (= "quux\nbaz\nbar\nfoo\n" (slurp out))))))

(deftest list-tasks-test
  (test-utils/with-config {}
    (let [res (test-utils/bb nil "tasks")]
      (is (str/includes? res "No tasks found."))))
  (test-utils/with-config {:tasks {'task1
                                   {:doc "task1 doc"
                                    :task '(+ 1 2 3)}
                                   'task2
                                   {:doc "task2 doc"
                                    :task '(+ 4 5 6)}
                                   '-task3
                                   {:task '(+ 1 2 3)}
                                   'task4
                                   {:task '(+ 1 2 3)
                                    :private true}}}
    (let [res (test-utils/bb nil "tasks")]
      (is (str/includes? res "The following tasks are available:"))
      (is (str/includes? res "task1 task1 doc"))
      (is (str/includes? res "task2 task2 doc"))
      (is (not (str/includes? res "task3")))
      (is (not (str/includes? res "task4"))))))

;; TODO:
;; Do we want to support the same parsing as the clj CLI?
;; Or do we want `--aliases :foo:bar`
;; Let's wait for a good use case
#_(deftest alias-deps-test
  (test-utils/with-config '{:aliases {:medley {:deps {medley/medley {:mvn/version "1.3.0"}}}}}
    (is (= '{1 {:id 1}, 2 {:id 2}}
           (bb "-A:medley" "-e" "(require 'medley.core)" "-e" "(medley.core/index-by :id [{:id 1} {:id 2}])")))))
