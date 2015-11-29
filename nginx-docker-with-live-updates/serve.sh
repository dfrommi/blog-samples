#!/bin/bash
docker run -v $(pwd)/app:/usr/share/nginx/html -v $(pwd)/app-nginx.conf:/etc/nginx/conf.d/default.conf -p 8080:80 --rm nginx