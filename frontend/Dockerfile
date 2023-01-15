FROM node:lts

EXPOSE 80
ENV NUXT_HOST=0.0.0.0
ENV NUXT_PORT=80

WORKDIR /opt/filespider

COPY package.json .
RUN yarn install

COPY . .
RUN yarn run build

ENTRYPOINT ["yarn", "run", "start"]
