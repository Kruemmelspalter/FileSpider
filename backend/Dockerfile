FROM eclipse-temurin:11

WORKDIR /opt/filespider

RUN mkdir /var/lib/filespider
VOLUME /var/lib/filespider

EXPOSE 80
ENTRYPOINT ["app/bin/app"]

RUN apt-get update -y && apt-get install -y texlive-full
RUN apt-get update -y && apt-get install -y xournalpp
RUN apt-get update -y && apt-get install -y pandoc
RUN apt-get update -y && apt-get install -y python3-pip npm nodejs
RUN npm install -g n && n stable
RUN pip install pandoc-mermaid-filter
RUN npm install -g @mermaid-js/mermaid-cli

COPY app/build/distributions/app.tar FileSpider.tar
RUN tar -xf FileSpider.tar
