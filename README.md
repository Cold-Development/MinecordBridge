# MinecordBridge
This is a plugin that can run commands on a Minecraft server when a specified role is given on a Discord server.
Idea is to give rewards on your **Minecraft** server when a user gets a donation role on a Discord server, such as **Discord Nitro** or **Patreon**.

# Current Features

- Set up an arbitrary amount of Discord roles!
- Each role has an arbitrary amount of commands to run when the Role is added and removed!
- Built-in verification system through Discord and Minecraft to prevent users registering other accounts

# How to set up

1. Get the BOT_TOKEN for the Discord bot you'd like to use.
2. Get the SERVER_ID for the Discord server you're using.
3. Get the ROLE_ID for the roles you would like to use.

# Configuration

Simply define how many roles you would like to have in the "roles:" category, then make a seperate category following the example formats with the same title as the name that you gave it in the "roles:" category. Then paste ROLE_ID in the respective "role-id:" spots and reload the plugin. Then add the commands you would like to run when the role is given, and taken away. You can use **"%user%"** as a placeholder for the user's Minecraft username as well!

# Config file

```yml
#################################################
# Main configuration for MinecordBridge
# Author: padrewin
#################################################

debug: false # If set to true, it will enable debug mode for detailed logging.

# Discord bot token
bot-token: "BOT_TOKEN" # The token of the Discord bot. Replace "BOT_TOKEN" with the actual token.

# Discord server ID
server-id: "SERVER_ID" # The ID of the Discord server where the bot is active. Replace "SERVER_ID" with the actual server ID.

# Settings for changing nickname on Discord upon linking
change-nickanme-on-link: false # If set to true, the bot will change the user's nickname on Discord when the Minecraft account is verified.

# Settings for chat stream (ChatStream)
enable-chatstream: false # If set to true, messages from Minecraft will be sent to a specific Discord channel.
chatstream-channel: "CHANNEL_ID" # The ID of the Discord channel where Minecraft chat messages will be sent. Replace "CHANNEL_ID" with the actual channel ID.
chatstream-message-format: "§f[§9Discord§f]%user%: %MESSAGE%" # The format of messages sent to the Discord channel. You can customize the format as you wish.
chatstream-use-permission-groups: false # If set to true, it will use permission groups to filter messages.

# Roles configuration
roles:
  - exampleRole1
  - exampleRole2

exampleRole1:
  role-id: "ROLE_ID" # The ID of the role. Replace "ROLE_ID" with the actual role ID.
  add-commands: # Commands to execute when the role is added
    - example command 1
    - example command 2
  remove-commands: # Commands to execute when the role is removed
    - example command 1
    - example command 2

exampleRole2:
  role-id: "ROLE_ID" # The ID of the role. Replace "ROLE_ID" with the actual role ID.
  add-commands: # Commands to execute when the role is added
    - example command 1
    - example command 2
  remove-commands: # Commands to execute when the role is removed
    - example command 1
    - example command 2
```

# Verification:

1. When the role is added, the user on Discord who received the role will be sent a message by the bot you used the token with asking the user if they have a Minecraft account they would like to link and receive rewards for. From this point on, the user can type "cancel" to end the process at any of the following steps.

2. If the user responds with "no", the bot will stop asking and nothing else happens. If the user responds with "yes", it will prompt them to  join the Minecraft server, then send their username to the bot.

3. If the player is not on the server, it will ask them to repeat. If they are, plugin will send them a code on the Minecraft server, which they send to the bot.

4. If the code is correct, the plugin will run the role-add commands.

5. When the role is removed, the plugin will automatically run the role-remove commands.

6. Done!

# Commands
`<>` = Required Argument
`()` = Optional Argument

- `/minecord reload` - Reloads the configuration file.
- `/minecord link <Discord-username>` - Initiate the linking process.
- `/minecord unlink` - Unlink your account from the Discord bot.
- `/minecord retroLink (Discord-username) (role-name)` - Retroactively send link requests to all users in a role that existed before plugin installation (or if a role existed before being added as a rewarded role on Minecraft). Can also be done on a single player.

# Permissions

- `minecord.update` - Allows the user to be notified if there is a plugin update available.
- `minecord.command.link` - Allows the user to use the `/minecord link` command.
- `minecord.command.unlink` - Allows the user to use the `/minecord unlink` command.
- `minecord.command.retroLink` - Allows the user to use the `/minecord retroLink` command.
- `minecord.command.reload` - Allows the user to use the `/minecord reload` command.