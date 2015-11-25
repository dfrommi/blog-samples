---
posted: 2015-07-30
tags: [nginx, docker, bugfix]
---

# Enable live updates in Nginx Docker container

Running *nginx* from Docker is a piece of cake and hosting local files are equally easy. But there's one catch if you're using *boot2docker*, at least on a Mac: live updates are not working. Changes on local files are not reflected in *nginx* immediately, but a container restart is required. This post describes how to work around this limitation.

Some googling quickly reveals that the reason is a known incompatibility between *VirtualBox* and *sendfile*. The workaround is to disable *sendfile* in *nginx* configuration, which is done by adding `sendfile off` directive to nginx's server config section.

The probably easiest way to do this is overriding the container's `default.conf` file (contains *nginx* server config) by a custom one. Store following file as `app-nginx.conf` in a working directory of your choice.

```
server {
    listen       80;
    server_name  localhost;

    location / {
        root   /usr/share/nginx/html;
        index  index.html;
    }

    sendfile	off;
}
```

Note that `sendfile off` is part of the configuration to work around the live update issue.

To test if everything works fine, let's create an `index.html` file in `app` subdirectoy, like that one

```html
<html>
  <body>
    <h1>Hello from Docker</h1>
  </body>
</html>
```

Now we can start the nginx container. I put in a wrapper script. It needs to be executed from the directory containing `app-nginx.conf` file and `app` subdirectoy.

```bash
#!/bin/bash
docker run -v $(pwd)/app:/usr/share/nginx/html -v $(pwd)/app-nginx.conf:/etc/nginx/conf.d/default.conf -p 8080:80 --rm nginx
```

The important part is the second volume mapping. It maps our `app-nginx.conf` file as nginx's `default.conf` and therefore overrides default server config.

Now browse to `http://192.168.59.103:8080/` (or whatever your boot2docker IP is) and you'll see a friendly *Hello from Docker*. Change the text in `index.html` file, reload page in Browser and tadaaa, live update.