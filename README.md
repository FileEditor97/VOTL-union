# VOTL (UnionTeam's)
 [![Publish Release](https://github.com/FileEditor97/VOTL-union/actions/workflows/gradle.yml/badge.svg)](https://github.com/FileEditor97/VOTL-union/actions/workflows/gradle.yml)  
 Voice of the Lord - discord bot written in Java using JDA library.  
 Functions: server moderation and sync blacklists, custom voice channels and verification, ticketing.  

 Visit https://votl.fileeditor.dev/ to learn more about available commands and to view documentation.  

 This is modified version of [public bot (VOTL)](https://github.com/FileEditor97/VOTL) for [UnionTeam](https://unionteams.ru) Discord servers.  

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
	"helper-token": ""
 }
 ```

## Inspiration/Credits
 Thanks to Chew (JDA-Chewtils and Chewbotcca bot) and jagrosh (JDA-Utilities)  
 [PurrBot](https://github.com/purrbot-site/PurrBot) by Andre_601 (purrbot.site)  
 [AvaIre](https://github.com/avaire/avaire) by Senither  
 Ryzeon & Inkception for [Discord (JDA) HTML Transcripts](https://github.com/Ryzeon/discord-html-transcripts)  
 [Prometheus](https://github.com/prometheus/client_java) contributors
