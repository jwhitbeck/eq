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
$ echo '{:id 1 :name "foo" :geo {:country "US"}}{:id 2 :name "bar" :geo {:country "FR"}}' | eq

{
  :id 1
  :name "foo"
  :geo {
    :country "US"
  }
}
{
  :id 2
  :name "bar"
  :geo {
    :country "FR"
  }
}
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

{
  :id 1
  :name "foo"
}
{
  :id 2
  :name "bar"
}
```
```
$ echo '{:id 1 :name "foo" :geo {:country "US"}}{:id 2 :name "bar" :geo {:country "FR"}}' \
  | eq --apply-dissoc '[:name :geo]'

{
  :id 1
}
{
  :id 2
}
```
```
$ echo '{:id 1 :name "foo" :geo {:country "US"}}{:id 2 :name "bar" :geo {:country "FR"}}' \
  | eq --select-keys '[:id :geo]'

{
  :id 1
  :geo {
    :country "US"
  }
}
{
  :id 2
  :geo {
    :country "FR"
  }
}
```

## Download

Just download eq from [here](https://raw.githubusercontent.com/jwhitbeck/eq/master/eq) and place it on your PATH.

## Requirements

`eq` is writen in [ClojureScript][clojurescript] targeting [node.js][nodejs]. Therefore it requires a recent version of node.js and expects the `node` executable to be on your PATH.

On some platforms (e.g., Ubuntu), the node.js binary is `nodejs` instead of `node`. If so, then you need to edit `eq` and change the shebang line from `#!/usr/bin/env node` to `#!/usr/bin/env nodejs`. Alternatively, you can just use the following one-liner: `sed -i 's/node/nodejs/' eq`.

[clojurescript]: https://github.com/clojure/clojurescript
[nodejs]: http://nodejs.org/

## License

Copyright &copy; 2014 John Whitbeck

Distributed under the Eclipse Public License, the same as ClojureScript.
