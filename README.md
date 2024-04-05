# VOTL (UnionTeam's)
 [![Publish Release](https://github.com/FileEditor97/VOTL-union/actions/workflows/gradle.yml/badge.svg)](https://github.com/FileEditor97/VOTL-union/actions/workflows/gradle.yml)  
 Voice of the Lord - discord bot written in Java using JDA library.  
 Functions: server moderation and sync blacklists, custom voice channels and verification, ticketing.  

 Visit main website https://votl.fileeditor.dev/  

 This is modified version of [public bot (VOTL)](https://github.com/FileEditor97/VOTL) for [UnionTeam's](https://unionteams.ru) Discord servers.  

## Config file
 data/config.json:
 ```json
 {
	"bot-token": "",
	"owner-id": "owner's ID",
	"dev-servers": [
		"dev server's IDs"
	],
	"webhook": "link to webhook, if you want to receive ERROR level logs",
	"website-ip": "ip without port",
	"website-user": "username",
	"website-pass": "password",
	"central-ip": "ip without port",
	"central-user": "username",
	"central-pass": "password",
	"central-dbs": {
		"first guild id": {
			"db name": "1 server/db title",
			"db name": "2 server/db title"
		},
		"second guild id": {
			"db name": "3 server/db title"
		}
	},
	"helper-token": ""
 }
 ```

## Inspiration/Credits
 Thanks to Chew (JDA-Chewtils and Chewbotcca bot) and jagrosh (JDA-Utilities)  
 [PurrBot](github.com/purrbot-site/PurrBot) by Andre_601 (purrbot.site)  
 Inkception for [Discord (JDA) HTML Transcripts](https://github.com/Inkception/discord-html-transcripts)
