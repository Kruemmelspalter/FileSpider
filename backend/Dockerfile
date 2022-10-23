FROM eclipse-temurin:11

WORKDIR /opt/filespider

RUN mkdir /var/lib/filespider
VOLUME /var/lib/filespider

EXPOSE 80
ENTRYPOINT ["app/bin/app"]

RUN apt-get update -y && apt-get install -y texlive-full # LaTeX
RUN apt-get update -y && apt-get install -y xournalpp # Xournal++
RUN apt-get update -y && apt-get install -y pandoc # pandoc (markdown)

COPY app/build/distributions/app.tar FileSpider.tar
RUN tar -xf FileSpider.tar