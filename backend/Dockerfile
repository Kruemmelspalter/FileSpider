FROM eclipse-temurin:11

WORKDIR /opt/filespider

RUN mkdir /var/lib/filespider
VOLUME /var/lib/filespider

COPY app/build/distributions/app.tar FileSpider.tar
RUN tar -xf FileSpider.tar

EXPOSE 80
ENTRYPOINT ["app/bin/app"]
