FROM eclipse-temurin:11

WORKDIR /opt/filespider

RUN mkdir /var/lib/filespider
VOLUME /var/lib/filespider

EXPOSE 80
ENTRYPOINT ["app/bin/app"]

RUN apt-get update -y
RUN apt-get install -y texlive-latex-extra

COPY app/build/distributions/app.tar FileSpider.tar
RUN tar -xf FileSpider.tar