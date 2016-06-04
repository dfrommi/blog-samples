---
posted: 2016-06-04
tags: [docker, logstash]
---

# Local Logstash Testing

*Logstash* is famous for being the *L* in *ELK*. It is a log processing tool that tries to make sense out of mostly unstructured log messages by given parsing rules. 
This article descibes how I develop and test Logstash rules on my local machine, before applying them to production environment.

The setup look like this:

- Logstash Docker container runs locally with configuration auto-reloading activated
- Log statements are sent to Logstash via `http`
- Parsed output is printed in Docker log

## Project structure
Logstash cannot only handle a single configuration file, but also a directory with multiple files. They are merged in alphabetical order.
My `conf` folder contains all configuration files and a `patterns` subfolder with pattern definitions:

- `01-in-out.conf`
- `02-common.conf`
- `10-gateway.conf`
- `20-mail.conf`
- `patterns`
    - `dovecot`
    - `nginx`
    - `postfix`

## Logstash Auto Reloading
Logstash can monitor and reload configuration files or directories. That is very nice during development, because you can write rules and test them without time consuming application restarts. The following command monitors `/conf` directory and reloads if required:

```bash
logstash -f /conf --auto-reload
```

There is one limitation: not all input plugins are compatible with auto-reload, `stdin` for example is not. If you still try it, you will see the rather cryptic message 

> Logstash was not able to load configuration since it does not support
> dynamic reloading and -r or --auto-reload flag was enabled {:pipeline_id=>"main", :plugins=>[LogStash::Inputs::Stdin], :level=>:error}

For that reason, I'm using `http` input and send my log messages with `curl` to Logstash.

Input and output definitions are in a separate file, `01-in-out.conf` and I switch from production to development mode by commenting lines. Keeping it in a separate file reduces the risk to accidentially commit the development version. 

This is my current configuration in development mode

```ruby
input {
  http {}
  #gelf { port => 12201 }
}

output {
  #elasticsearch { hosts => [ "elasticsearch:9200" ] }
  stdout { codec => rubydebug }
}
```
 
## Logstash Docker Container
Logstash is running in a Docker container on production, and I do the same during development. That allows me to use the same absolute path on both environments and `patterns_dir` value can remain unchanged.

```bash
docker run -it --rm -v $(pwd)/conf:/conf -p 8080:8080 logstash logstash -f /conf --auto-reload
```

It runs the official Logstash container, exposes port *8080* (used by `http` input) and mounts the `conf` subdirectory to `/conf`. Then it starts Logstash as described above with auto-reloading.

## Send Log Input
A single log line can be sent to Logstash using `curl`

```bash
curl -d "This is a log entry" http://localhost:8080/
```

Parsed output will appear in Docker logs.

To send structured logging input, for example with a container name and a message, save it in a file using JSON format

```json
{
  "container_name": "mail",
  "message": "This is a log entry"
}
```

And then send it to Logstash using

```bash
curl -H "Content-Type: application/json" -d "@mail.json" http://localhost:8080/
```

## Summary
This development setup allows to develop Logstash filters by using the same input over and over again until the desired result is reached. At the same time, filters can be fully tested on real input without modification of the real system.
