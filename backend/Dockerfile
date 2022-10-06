FROM eclipse-temurin:11

WORKDIR /opt/filespider
COPY app/build/distributions/app.tar FileSpider.tar
RUN tar -xf FileSpider.tar

ENTRYPOINT ["app/bin/app"]
