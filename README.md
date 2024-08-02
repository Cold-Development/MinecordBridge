# MinecordBridge
MinecordBridge is a Minecraft plugin designed to reward players with in-game benefits for boosting your Discord server with Nitro. This integration helps create a cohesive community experience by bridging the gap between your Minecraft server and Discord server.

# Key Features

- Discord Nitro Boost Rewards: Automatically rewards players in Minecraft when they boost your Discord server with Nitro.
- Customizable Messages: All messages sent by the plugin can be customized via the messages.yml file.
- Role Management: Synchronizes roles and permissions between Minecraft and Discord.
- Admin Commands: Provides commands to link and unlink Discord accounts with Minecraft accounts, reload plugin configurations.

# How to set up

1. Get the BOT_TOKEN for the Discord bot you'd like to use.
2. Get the SERVER_ID for the Discord server you're using.
3. Role Setup: Configure roles and associated commands in config.yml.
4. Messages: Customize all plugin messages in the messages.yml file.

# Configuration

Simply define how many roles you would like to have in the "roles:" category, then make a seperate category following the example formats with the same title as the name that you gave it in the "roles:" category. Then paste ROLE_ID in the respective "role-id:" spots and reload the plugin. Then add the commands you would like to run when the role is given, and taken away. You can use **"%user%"** as a placeholder for the user's Minecraft username as well!

# Config file

```yml
#################################################
# Main configuration for MinecordBridge
# Author: padrewin
#################################################

# Plugin tag configuration
plugin-tag: "&8「&#6E00A5M&#7914B2i&#8427BFn&#8F3BCCe&#9A4FD8c&#A563E5o&#B076F2r&#BB8AFFd&8」&7»&f " # The tag to be used in front of messages.

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
chatstream-message-format: "&f[&9Discord&f]%USER%: %MESSAGE%" # The format of messages sent to the Discord channel. You can customize the format as you wish.
chatstream-use-permission-groups: false # If set to true, it will use permission groups to filter messages.

# Roles configuration
roles:
  - exampleRole

exampleRole:
  role-id: "ROLE_ID" # The ID of the role. Replace "ROLE_ID" with the actual role ID.
  add-commands: # Commands to execute when the role is added
    - example command 1
    - example command 2
  remove-commands: # Commands to execute when the role is removed
    - example command 1
    - example command 2

```

# Messages file

```yaml
# Pay close attention! This config you'll find my actual server name "mc-1st.ro". Chage it to your Minecraft server name.
# Auto role messages. Those messages appear in the user's Discord DM with your Bot in order to link his account and get the rewards. Or maybe cancel the process.
Auto-Role:
  boost_message: "Hmmm! :thinking: I noticed that you boosted our Discord server! :rocket:"
  registration_question: "Are you registered on our Minecraft server? *(mc-1st.ro)* Reply by writing \"YES\" or \"NO\"."
  dm_instructions: "__**Follow these instructions:**__\n:one: Connect to **/lobby** on the **mc-1st.ro** server using the account you want to claim rewards for.\n:two: Write your Minecraft account name in this DM. __(the name must be exact)__\n:three: Enter the code received in the Minecraft chat in this DM.\n:four: Enjoy the benefits! :tada:"
  dm_enter_minecraft_name: "To start, enter your Minecraft name. __(the name must be exact)__"
  dm_thank_you: "In this case, we cannot reward you, but we still thank you for your support! :heart:"
  dm_retry_code: "Enter the code sent in the Minecraft chat. Type \"resend\" if you need a new code!"
  dm_not_connected: "You are not connected in **/lobby** on the **mc-1st.ro** Minecraft server! Make sure you have typed your name correctly."
  dm_player_not_found: "Player not found! Make sure you are connected in **/lobby** on the server and have written your name correctly!"
  dm_rewarded: "You have been rewarded, thank you for your support! :heart:"
  dm_code_mismatch: "The code entered does not match. Type \"resend\" if you want to receive a new code."
  dm_something_wrong: "Something went wrong, contact one of the Owners. :angry:"
  dm_cancelled: "You have canceled the verification process."
  dm_new_code_sent: "A new code has been sent. Enter the code sent in the Minecraft chat."
  dm_new_code_not_possible: "You cannot send a new code at this time."

# Player facing messages. Those messages can be seen by the player in his Minecraft chat.
Player-facing-messages:
  rewarded: "You have been rewarded!"
  new_code: "The new code is: §4%code%"
  your_code: "Your code is: §4%code%"

# Commands messages. Those messages are ok to be left in English since you're the only one who is viewing this.
Commands:
  reload_success: "&fConfig &2reloaded&f!"
  command_not_found: "&cCommand not found!"
  invalid_usage_retrolink: "&fInvalid command usage. Use &c/minecord retrolink <username#0> <role>"
  role_mandatory: "&cRole is mandatory. &fPlease specify a role. &7(&fexample: &2Nitro&7)"
  invalid_username_format: "&cInvalid username format. &fPlease include a discriminator. &7(&fexample: &2user#0&7)"
  instructions_sent: "&fInstructions sent &2successfully&f."
  error_linking_user: "&cError linking user: "
  invalid_usage_unlink: "&fInvalid command usage. Use &c/minecord unlink <username#0> <role>"
  role_not_found: "&cRole not found: "
  note_case_sensitive: "Note that role names are case-sensitive."
  player_not_found_discord: "&cPlayer not found on &9Discord&c server or discriminator is incorrect!"
  no_linked_account: "&cNo linked account found for this &9Discord&f user!"
  no_minecraft_username: "&cNo Minecraft username linked for this Discord user!"
  account_unlinked: "&fUser's account is now &cunlinked&f and benefits are removed!"
  account_already_linked: "&fAccount already &2linked&f!"
  player_not_found_discord_simple: "&cPlayer is not found on &9Discord &cserver or discriminator is not found!"
  linking_dm: "You are attempting to link your Discord and Minecraft accounts.\nAnswer with \"**YES**\" to continue or \"**NO**\" if you think this was an error."
  check_dm: "&fCheck your DM's on &9Discord &fto continue!"
```


# Verification:

1. When the role is added, the user on Discord who received the role will be sent a message by the bot you used the token with asking the user if they have a Minecraft account they would like to link and receive rewards for. From this point on, the user can type "cancel" to end the process at any of the following steps.

2. If the user responds with "no", the bot will stop asking and nothing else happens. If the user responds with "yes", it will prompt them to  join the Minecraft server, then send their username to the bot.

3. If the player is not on the server, it will ask them to repeat. If they are, plugin will send them a code on the Minecraft server, which they send to the bot.

4. If the code is correct, the plugin will run the role-add commands.

5. When the role is removed, the plugin will automatically run the role-remove commands.

6. Done!

# Commands

- **/minecord reload** - Reloads the plugin's configuration.
- **/minecord retrolink <username#discriminator> <role>** - Link a Discord account to a Minecraft account.
- **/minecord unlink <username#discriminator> <role>** - Unlinks a Discord account from a Minecraft account.

# Permissions

- `minecord.admin` - Full control over the plugin.

# Benefits

- Enhanced Community Engagement: Encourages players to boost your Discord server by offering in-game rewards.
- Easy Administration: Simplifies managing the community across both platforms.
- Fully Customizable: Offers flexibility with message customization and plugin behavior.
- This plugin is perfect for Minecraft servers looking to incentivize Discord Nitro boosts by providing tangible in-game rewards, thereby enhancing player engagement and community building.