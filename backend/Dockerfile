FROM eclipse-temurin:11

WORKDIR /opt/filespider

RUN mkdir /var/lib/filespider
VOLUME /var/lib/filespider

EXPOSE 80
ENTRYPOINT ["app/bin/app"]

COPY app/build/distributions/app.tar FileSpider.tar
RUN tar -xf FileSpider.tar