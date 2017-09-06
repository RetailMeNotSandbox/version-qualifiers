# Version Qualified

Version Qualified gives you the ability to annotate your code with version
information in order to make backwards-compatible changes easier to deal with
while you are growing your API.

# Motivation & Rationale

The Mobile API team at RetailMeNot was tasked with having to support a content
schema that required near-infinite backwards-compatibility, and that changed
very frequently. An ad-hoc solution for writing code to generate data with many
slight alterations based on runtime client requirements was unacceptable.

From this we realized the general problem of writing versioned code, and came
up with this solution.

## The Problem

Making slightly different versions of the same code can be time consuming, and
ugly. Two approaches which generalize to nearly all cases exist:

The first is to copy-and-paste your function, making whatever changes are
necessary. This is the most simple technique in the sense that you can
understand the entire implementation for a version by just looking at a single
piece of code; the concept of a "version" is not intertwined with the
functionality of the version-sensitive code. However, this is also a difficult
approach: it bloats the codebase via repetition.

```clojure
(defn widgets-v1 [] [widget-1 widget-2])

(defn widgets-v2 [] [widget-1 widget-2 widget-3])
```

Copy-paste is usually a code smell, and rightly so many developers opt instead
for the second approach.

The second approach, lets call it "call-and-mutate", is to make a new function
which calls the old - or perhaps some shared code that you've now abstracted
out of the original version - which then which takes whatever steps are
required to turn the old into the new version. No code is duplicated *but*
you've switched mindsets from writing code for a single version at a time, to
instead coding how to transform the first solution into the second; you're
complecting newer versions of functionality with older ones. Doing this will
quickly make you lose sight of what exactly it is you're computing for any
given version; in extreme cases you'll have to fully understand an entire call
stack to get the complete picture. This can reduce the number of shotgun edits
you have to make though, potentially making the maintenance story easier
overall.

```clojure
(defn widgets-v1 [] [widget-1 widget-2])

;; Note: order matters. We assume widgets-v1 is a vector
(defn widgets-v2 [] (conj widgets-v1 widget-2))
```

Both of these approaches push the responsibility of selecting the right version
to their caller.

```clojure
(defn render-widgets
  [version]
  (case version
    :v1 (render (widgets-v1))
    :v2 (render (widgets-v2))))
```

## This Solution

A version qualified solution is a hybrid of the two above approaches. Instead
of developing a chain of functions, or copy pasting the same code and tweaking
it, you can keep all your code under one roof and just mark it up with
annotations, which are used to generate version-specific code for each known
version that you need.

```clojure
(defn widgets []
  (versioned 
    [widget-1 widget-2 (added :V2 widget-3)]))
```

This makes your life easier in a number of ways:

* Your code can stay DRY. Code which remains constant between versions doesn't
  need to be duplicated, or packaged into some construct so that you can reuse
  it
* Version sensitive code can stay localized, which allows you to get the
  complete picture for some functionality all in one place
* You can code an expressive solution, rather than having to write
  transformations between versions (as "call-and-mutate" would require)
* Promotes a "code version" to a first-class concept with distinct syntax and
  patterns
* Provides a standardized way of selecting which version to execute

Of course, the major cost for doing this is _complexity_: where there is
version-qualified code we are explicitly interleaving *every* version's
implementation together. For code with large variations between versions, this
can quickly become a spaghetti nightmare; for code with small variations, the
locality can dramatically improve understanding.
