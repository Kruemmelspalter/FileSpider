const express = require('express')
const proxy = require('express-http-proxy')
const { execSync, spawn } = require("child_process")
const axios = require('axios')
require('dotenv').config()

const API_HOST = process.env.API_HOST || 'http://172.31.69.4'
const MOUNT_PATH = process.env.MOUNT_PATH || (process.env.HOME + '/.filespider')
const PORT = process.env.PORT || 8080

const app = express()

const editors = {
	mime: path => ({explorer: true, command: 'xdg-open', args: [path]}),
	plain: path => ({explorer: true, command: 'kate', args: [path]}),
	xournalpp: path => ({explorer: true, command: 'xournalpp', args: [path]}),
}

function mountWebDAV(host) {
	execSync(`mkdir -p ${MOUNT_PATH}`)
	console.log('created directory')
	execSync(`konsole -e 'sudo mount -t davfs -o uid=${process.getuid()},gid=${process.getgid()} ${API_HOST}/files/ ${MOUNT_PATH}'`)
	console.log('mounted')
}

function unmountWebDAV() {
	execSync(`konsole -e 'sudo umount ${MOUNT_PATH}'`)
}

app.post('/document/:docId/edit', async (req, res) => {
	var meta
	try {
		meta = await axios.get(`${API_HOST}/document/${req.params.docId}`)
	} catch(e) {
		res.status(500).send({path: `${API_HOST}/document/${req.params.docId}`, error: e})
		return
	}
	const path = `${MOUNT_PATH}/${req.params.docId}/${req.params.docId}` + meta.fileExtension !== null ? "." + meta.fileExtension : ""
	const editorProvider = editors[meta.data.editor]
	if(editorProvider === undefined) {
		res.status(400).send(`Wrong editor ${meta.data.editor}`)
		console.log(`Wrong editor ${meta.data.editor}`)
		return
	}
	const editor = editorProvider(path)
	if(editor.explorer) spawn('dolphin', [`${path}/..`])
	spawn(editor.command, editor.args)
	res.send()
})

app.use('/', proxy(API_HOST))

var server
async function startServer() {
	await mountWebDAV(API_HOST)
	server = app.listen(PORT, () => console.log(`Server listening on http://localhost:${PORT}`))
}


function stopServer() {
	console.log('Unmounting...')
	if(server?.close !== undefined) server.close()
	unmountWebDAV()
}

process.on('SIGINT', stopServer)

startServer()
