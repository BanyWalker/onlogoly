FROM nginx:latest
RUN rm /usr/share/nginx/html/*
ENV TZ="Asia/Shanghai"

COPY ./nginx.conf /etc/nginx/conf.d/default.conf
WORKDIR /usr/share/nginx/html
ADD ./dist/ .

EXPOSE 80
