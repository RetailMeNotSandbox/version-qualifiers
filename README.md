# Introduction

## Overview

version-qualified is a library which expands code annotated with version
"qualifiers" into separate code paths which can be switched at runtime.

The purpose is let developers enhance their source code but while maintaining
backwards compatibility. It attempts to do so while drastically reducing
copy-pasta / boilerplate.

## Installation

[![Clojars Project](...svg)](...)

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
    (+ (added :V0 1)
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

## Prepackaged qualifiers

### Linear qualifiers

### Feature qualifiers

## Writing your own


