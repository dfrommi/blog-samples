---
posted: 2019-01-12
tags: [fish, config]
comments: 18
---

# Modular Fish-Shell Configuration

Fish shell configuration is split into functions, completions and initalization code. But it's easy to split it further on a *per feature* basis.

The default fish configuration structure looks something like this:

```
~/.config/fish/
  - functions/
  - completions/
  - conf.d/
```

This structure is repeated per feature, what I call `krill`.

```
~/.config/fish/
  - functions/
  - completions/
  - conf.d/
  - krill
    - git_tools
      - functions/
      - completions/
      - conf.d/
    - touchbar
      - functions/
      - conf.d/
```

To inform *fish* about the additional directories, the following code has to be executed on startup, for example by putting it in the file `~/.config/fish/conf.d/_krill.fish`:

```fish
set krill_path $HOME/.config/fish/krill

set fish_function_path (find $krill_path -depth 2 -type d -name functions) $fish_function_path
set fish_complete_path (find $krill_path -depth 2 -type d -name completions) $fish_complete_path

for file in (find $krill_path -path "*/conf.d/*.fish" -depth 3 -type f)
    builtin source $file 2> /dev/null
end
```

And that's it. Now the additional functions, completions and initialization code is known to fish and can be used.
