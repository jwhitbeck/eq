# Eq

A simple CLI tool for processing [EDN][edn] data.

[edn]: https://github.com/edn-format/edn

## Basic usage

```
$ eq -h

Usage: eq [OPTIONS]

  -c, --compact              Compact output, don't pretty-print
  -d, --dissoc K         []  Apply #(dissoc % K) to each parsed edn object.
      --apply-dissoc KS  []  Apply #(apply dissoc % KS) to each parsed edn object.
  -g, --get K            []  Apply #(get % K) to each parsed edn object.
      --get-in KS        []  Apply #(get-in % KS) to each parsed edn object.
  -s, --select-keys KS   []  Apply #(select-keys % KS) to each parsed edn object.
  -v, --version              Prints the eq version
  -h, --help

Multiple --dissoc, --apply-dissoc, --get, --get-in, --select-keys options may be passed,
in which case the output of each will be on a separate line.
```

#### Pretty printing

```
$ echo '{:id 1 :name "foo" :geo {:country "US" :region "CA"} :date "2000-01-01T00:00:00Z"}' | eq

{:date "2000-01-01T00:00:00Z",
 :name "foo",
 :geo {:region "CA", :country "US"},
 :id 1}
```

#### Field extraction

```
$ echo '{:id 1 :name "foo" :geo {:country "US"}}{:id 2 :name "bar" :geo {:country "FR"}}' \
  | eq --get :id --get-in '[:geo :country]' | xargs -n2

1 US
2 FR
```

#### Map manipulation

```
$ echo '{:id 1 :name "foo" :geo {:country "US"}}{:id 2 :name "bar" :geo {:country "FR"}}' \
  | eq --dissoc :geo

{:name "foo", :id 1}
{:name "bar", :id 2}
```
```
$ echo '{:id 1 :name "foo" :geo {:country "US"}}{:id 2 :name "bar" :geo {:country "FR"}}' \
  | eq --apply-dissoc '[:name :geo]'

{:id 1}
{:id 2}
```
```
$ echo '{:id 1 :name "foo" :geo {:country "US"}}{:id 2 :name "bar" :geo {:country "FR"}}' \
  | eq --select-keys '[:id :geo]'

{:id 1, :geo {:country "US"}}
{:id 2, :geo {:country "FR"}}
```

## Install

```bash
npm install -g edn-eq
```

## Requirements

`eq` is written in [ClojureScript][clojurescript] targeting [node.js][nodejs]. Therefore it requires a
reasonably recent version of node.js (>=0.10) and expects the `node` executable to be on your PATH.

On Ubuntu, the node.js binary is `nodejs` instead of `node`. Installing the `nodejs-legacy` package will add
the appropriate symlink.

[clojurescript]: https://github.com/clojure/clojurescript
[nodejs]: http://nodejs.org/

## License

Copyright &copy; 2014-2017 John Whitbeck

Distributed under the Apache License, Version 2.0.
