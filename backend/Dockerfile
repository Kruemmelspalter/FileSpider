FROM eclipse-temurin:11

WORKDIR /opt/filespider
COPY app/build/distributions/app.tar FileSpider.tar
RUN tar -xf FileSpider.tar

CMD ["app/bin/app"]
