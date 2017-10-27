# Introduction

## Overview

version-qualified is a library which expands code annotated with version
"qualifiers" into separate code paths which can be switched at runtime.

The purpose is let developers enhance their source code but while maintaining
backwards compatibility. It attempts to do so while drastically reducing
copy-pasta / boilerplate.

## Installation

[![Clojars Project](https://img.shields.io/clojars/v/com.rmn/version-qualified.svg)](https://clojars.org/com.rmn/version-qualified)

## Quickstart

Code that you want to version will need to be wrapped with a macro. This
library defines no macros; however, it gives you a set of tools to help you
build one. Theoretically, here is everything required to get started:

```clj
(require '[com.rmn.version-qualified.core :as v])

(declare ^:dynamic *current-app-version*)

(def my-app-versions [:V0 :V1 :V2])

(defmacro versioned
  [body]
  (v/version-qualified `*current-app-version* known-versions body))
```

Here we're declaring a few things:

* A dynamic variable which represents the desired version of code to execute
* A vector of all the known versions of the app
* A macro with which you can wrap code that depends on the version

Note: `version-qualified` is a function, but it behaves like a macro in that it
takes code as input, and returns code as output.

I said "theoretically" that is everything you need: with just the above,
version-qualified wont recognize any version qualifiers. Lets setup our linear
qualifiers:

```clj
(require '[com.rmn.version-qualified.qualifiers.linear :as linear])

(defmacro versioned
  [body]
  (binding [linear/*known-versions* known-version]
    (v/version-qualified `*current-app-version* known-versions body)))
```

Now, any code wrapped in `versioned` can use the "linear" qualifiers `added`,
`removed`, `only`, `switch` and `changed`:

```clj
(defn do-math []
  (versioned
    (+ (added :V0 1)  ;; <-- here is a (linear) version qualifier
       (added :V1 1)
       (added :V2 1))))

;; user=> (binding [*current-app-version* :V0] (do-math))
;; 1
;; user=> (binding [*current-app-version* :V1] (do-math))
;; 2
;; user=> (binding [*current-app-version* :V2] (do-math))
;; 3
```

Yay, It works! Notice that you must bind `*current-app-version*` before calling
code which is `versioned`. One great place to do this might be, for example,
some custom ring middleware which determines the version of an API based on the
request's Accept header.

# Version Qualifier Deep Dive

Qualifiers are parenthesis bracketed expressions. We name them after the first
symbol in the list. In the example under Quickstart, there were 3 expressions,
each of which was qualified with the `added` qualifier. Here are more examples
from some of the qualifiers you can find in this library:

```clj
(added :V5 ...)

(removed :V2 ...)

(feature :my-awesome feature ...)

(changed "first I was this"
         :V2 "Then I was this"
         :V7 "And I ended up here")
```

Note that qualifiers may wrap multiple expressions:

```clj
(versioned
  (+ 1 (added :V1 2 3 4)))
;; user=> (binding [*current-app-version* :V0] (do-math))
;; 1
;; user=> (binding [*current-app-version* :V1] (do-math))
;; 10
```

This may be problematic *if* the qualifier is also the "top level" expresssion:

```clj
(versioned (added :V1 "demonstrates" "the" "problem"))
```

This will throw an exception during compilation because of how macros work in
Clojure. In this case, the macro will throw an exception because before `:V1`
the expression is empty, and also because after and including `:V1` there are
multiple expressions; Clojure doesn't support macros returning multiple or zero
expressions.

## Writing your own

Version-qualifiers can be added by adding a method to the `eval-qualifier`
multimethod defined in the core namespace. They're fairly simple;
version-qualifiers are just functions which take the user's qualified source
code, and return a list of code that is appropriate for whatever version is
being compiled. They may return a special value `::v/delete` which will tell
the `version-qualified` function to omit that expression from the generated
code. Here is an implementation for the extremely simple qualifier `only`:

```clj
(defmethod v/eval-qualifier 'only
  [_ version-set & forms]
  (if (contains? version-set v/*version*)
    forms
    '(::v/delete)))

;; Example: (only #{:V1 :V3 :V5} ...)
```

The first argument `_` will always be `'only`, so we can ignore it;
`version-set` is a set-literal which the user specifies when they use the
qualifier; and `& forms` represents whatever code the user wishes to optionally
execute.

All this qualifier must do is check if `v/*version*`, which is bound by the
`version-qualified` function, is in the `version-set` specified by the user.
If so, simply return the user's code (in a list!), otherwise `'(::delete)`.

Note: The reason the return value must be stuffed into a list is that it is
possible for qualifier to return more than one expression, so we must force all
qualifiers to return a collection.

Sometimes, qualifiers need more information than only what is supplied in the
call. For example, the other linear qualifiers need to know the order of
possible app versions so that expressions like `(added :V4 ..)` are meaningful

One good method for giving them this extra context is with dynamic variables.
They work great because its typically pretty clumsy to have to include _all_
the information a qualifier needs at its call-site. Furthermore, since
qualifiers operate like macros, you would need to _literally_ embed that
information in the call, rather than referencing a var.

Recall from the quickstart:

```clj
(defmacro versioned
  [body]
  (binding [linear/*known-versions* known-version]
    (v/version-qualified `*current-app-version* known-versions body)))
```

`*known-versions*` is part of the linear namespace, and those qualifiers look
at this var to know the global ordering of versions:

```clj
(defmethod v/eval-qualifier 'added
  [_ added-version & forms]
  (if (>= (.indexOf *known-versions* v/*version*)
          (.indexOf *known-versions* added-version))
    forms
    (list ::v/delete)))
```

## Prepackaged qualifiers

Currently there are two sets of qualifiers available in this library. You can
chose to use one, both, or neither.

### Linear qualifiers

Linear qualifiers are so named because they rely on the inherent "linearity" of
application versions. As long as you have a _single_ line of versions, these
may be a good choice for you.

To enable them, you must require their namespace, and bind a dynamic var in
your macro. The dynamic variable `*known-versions*` is an ordered list of every
possible app version. Often times this can be the same var you pass to
`version-qualified`.

```clj
(require '[com.rmn.version-qualified.qualifiers.linear :as linear])

(def known-versions [:V0 :V1 :V2 :V3 ...])

(defmacro versioned
  [body]
  (binding [linear/*known-versions* known-version]
    (v/version-qualified `*current-app-version* known-versions body)))

;;; Examples

(added :Vn EXPR1 ... EXPRn)
(removed :Vn EXPR1 ... EXPRn)
(only #{:Vx, :Vy..} EXPR1 ... EXPRn)
(changed EXPR0
         :V1 EXPR1
         ...
         :Vn EXPRn)
```


### Feature qualifiers

Feature qualifiers allow you decouple your changes from your app versions, and
instead organize your code according to features.

To enable them, you must require their namespace, and bind a dynamic var in
your macro. The dynamic variable `version->feature`is a mapping of version in
`known-version` to a set of feature keywords.

```clj
(require '[com.rmn.version-qualified.qualifiers.feature :as feature])


(def version-manifest
  {:V0 #{:A}
   :V1 #{:A :B}
   :V2 #{   :B :C}})

;; Note that order doesn't matter when this is passed to v/version-qualified
(def known-versions (keys version-manifest))


(defmacro versioned
  [body]
  (binding [feature/*version->features* version-manifest]
    (v/version-qualified `*current-app-version* known-versions body)))


;;; Examples

(feature :some-feature EXPR1 ... EXPRn)

(feature-case
  :highest-priority-feature EXPR0
  ...
  :lowest-priority-feature EXPRn
  DEFAULT-EXPR)
```
