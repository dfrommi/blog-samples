set krill_path $HOME/.config/fish/krill

set fish_function_path (find $krill_path -depth 2 -type d -name functions) $fish_function_path
set fish_complete_path (find $krill_path -depth 2 -type d -name completions) $fish_complete_path

for file in (find $krill_path -path "*/conf.d/*.fish" -depth 3 -type f)
    builtin source $file 2> /dev/null
end
