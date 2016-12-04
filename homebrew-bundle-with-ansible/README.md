---
posted: 2016-12-04
tags: [homebrew, ansible]
comments: 15
---

# Homebrew Bundle with Ansible
[Bundle](https://github.com/Homebrew/homebrew-bundle) is a tap (aka extension) to run several [Homebrew](http://brew.sh) commands in one go. The list of commands is read from a plain-text *Brewfile*.
In contrast to [Ansible's Homebrew module](http://docs.ansible.com/ansible/homebrew_module.html) with a manually maintained items list, a *Brewfile* can be created from the currently installed Homebrew packages, making it a better fit for some use cases.
This article gives a short introduction to *Homebrew Bundle* and shows how to use it from *Ansible*.

## Homebrew Bundle in a Nutshell
Activate *Homebrew Bundle* with
```bash
brew tap homebrew/bundle
```

It has support for

- *taps*
- *packages*
- *casks*
- *Mac App Store*
    - requires [mas-cli](https://github.com/mas-cli/mas) to be installed, for example with `brew install mas`

A *Brewfile* can be created manually
```
tap 'caskroom/cask'
tap 'homebrew/bundle'
tap 'homebrew/core'
cask 'java'
brew 'ansible'
brew 'fish'
brew 'git'
cask 'alfred'
cask 'atom'
cask 'google-chrome'
mas 'Pixelmator', id: 407963104
mas 'Wunderlist', id: 410628904
```

Or the current list of installed taps, packages, casks and App Store software can be dumped with
```bash
brew bundle dump
```

Then you can

- check if everything is installed: `brew bundle check`
- install missing software: `brew bundle install`

## Use with Ansible
To use *Homebrew Bundle* with Ansible, three steps have to be executed:

1. install *Bundle* tap if required
2. check if everything in *Brewfile* is up-to-date
3. install missing software, if required

The following [role](roles/homebrew/tasks/main.yml) does the job.
```yaml
- name: Enable Homebrew bundle tap
  homebrew_tap:
    name: homebrew/bundle

- name: Check if Brewfile contains updates
  shell: brew bundle check --file="{{brewfile|default(Brewfile)}}"
  register: bundle_check_result
  ignore_errors: true

- name: Install apps using Brewfile
  shell: brew bundle install --file="{{brewfile|default(Brewfile)}}"
  when: bundle_check_result|failed
```

## TL;DR
*Brewfiles* are my preferred way of using Homebrew with Ansible. It is very convenient to just dump the current system state, instead of manually maintaining the list of installed packages. The support for Mac App Store is another advantage.

I also plan to use *Brewfile* in development projects. Let's say, a project requires a MySQL and an Influx database and some scripts are using `jq` for JSON processing, then this can be added to a *Brewfile*, put under source control and new developers just need to execute `brew bundle install` to get started.
