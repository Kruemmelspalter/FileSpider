# FileSpider
![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/kruemmelspalter/filespider/docker.yml)
![GitHub](https://img.shields.io/github/license/kruemmelspalter/filespider)
![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/kruemmelspalter/filespider)
![GitHub release (latest SemVer including pre-releases)](https://img.shields.io/github/v/release/kruemmelspalter/filespider?include_prereleases)
![GitHub code size in bytes](https://img.shields.io/github/languages/code-size/kruemmelspalter/filespider)
FileSpider is a software for organizing and editing documents.
Documents consist of a main file and optionally different assets like images.
For example:
- a markdown document
- an html document with audio files embedded
- a LaTeX document split into multiple other tex files and also images
These documents are organized via tags that can be searched for (positive and also negative filters)
For example:
- done homework in CS about Java: `school,homework,done,cs,java`
- homework that's still to do in German: `school,homework,german`
- a notebook entry in English about literature: `notebook,english,literature,school`
Example searches:
- `school`: matches all documents with the tag `school` -> All stuff related to school
- `homework,cs`: matches all documents with tags `cs` and `homework` -> All CS homework
- `homework,!done`: matches all documents with the tag `homework` and without the tag `done`-> All homework that still needs to be done
The documents are rendered in a specified way, for example:
- LaTeX files are rendered using pdflatex
- HTML files and images are just served plainly
- Markdown is rendered using pandoc and uses KaTeX for math

## Used Technologies
### Backend
The backend is written in Kotlin and uses Spring Boot and MySQL / MariaDB.
Also, it uses nginx for reverse-proxying and providing a WebDAV server for editing documents (with the [Client](#client))
The backend provides an interface for reading and writing document meta, for getting rendered documents and for creating and deleting ones
### Frontend
The frontend is written in Vue.js and Nuxt.js and uses Vuetify.js and Axios for HTTP requests.
### Client
The client is a small server written in Expres.js which proxies the Backend and adds an API endpoint for opening an editor (on a file mounted via WebDAV)

## Setup
### Server
An example for setting up FileSpider with Docker Compose can be found in [docker-compose.yml](docker-compose.yml)
### Client
The client can be found in [client/](client/). Just adjust the necessary values:
```js
const API_HOST = 'http://172.31.69.5' // where the backend / specifically nginx is hosted
const MOUNT_PATH = process.env.HOME + '/.filespider' // where to mount the WebDAV share
const PORT = 8080 // which port to open (for all ports below 1024, root privileges are needed)
```
## Usage
Assuming the client is started at `http://localhost:8080/`: Open a browser and navigate to `http://localhost:8080/`. 
- ## TODO Create a new document##
- If there already are documents, search for a document via entering appropriate tags in the search bar

## Features that could be added in the future
- Authentication / Support for multiple users
- more file types and more rendering / editing
- document meta (renderer, editor etc.) presets
- keyboard shortcuts for faster and easier editing
- CLI / TUI in addition to the web interface
