# The Angular app is built on the host first (see start-demo.sh); this image only serves it.
FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/shop-ui/browser /usr/share/nginx/html
