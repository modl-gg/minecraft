

### GUI Guide
Please create the inspect menu and the staff menu (+ commands to open them in-game). 
Each child menu is technically a seperate GUI, but make sure to reuse the parent menu elements in each. Use Cirrus GUI framework. You can pass
GUIs to other GUIs and open from that reference (to go back to previous menu). List GUIs should extend/implement AbstractBrowser in Cirrus with intercept method
to put static items for filter, page forward/back, and parent menu top items. Attempt to use best practices of reusing components when possible.

You will have to create a chat listener for chat prompts.

You can reference ../../HammerV2 for GUI framework usage and ../../Cirrus-3 (notably different Cirrus version since this one uses a HashMap intercept method
for AbstractBrowser instead of interceptBottomRow). 

Beds should be placed in the Q position in all secondary and tertiary menus to return to the previous menu. Also, if an inspect menu was opened from another inspect
menu or staff menu, the bed should first act as a back button in secondary/tertiary, then as return to the original menu button in primary menus. If in a primary menu,
and not opened from another menu, the bed should not be present (just blank space).

Inspect Menu (targeting a player):
-
Inspect Menu parent menu (primary), accessible with /inspect <player>:
````
* * * * * * * * *
* 1 2 3 4 5 * 6 *
* * * * * * * * *
* * * * * * * * *
Q * * * * * * * *
````
> * is blank space

> 1 is the player's head. Title: "{player}'s information."
> lore is player info (uuid, gameplay status, social status, active punishments, ip info, playtime)
> no action when clicked

> 2 is a paper item. Title: "Notes"
> lore is "View and edit {player}'s staff notes (# of notes)"
> switch to notes tab when clicked

> 3 is a vine item. Title: "Alts"
> lore is "View {player}'s known alternate accounts (# of alts)"
> switch to alts tab when clicked

> 4 is a book and quill item. Title: "History"
> lore is "View {player}'s past punishments (# of punishments)"
> switch to punishment history tab when clicked

> 5 is an eye of ender item. Title: "Reports"
> lore is "View and handle reports filed against {player} (# of reports)"
> switch to reports tab when clicked

> 6 is a mace item (1.21+) or bow item (1.20 and below). Title: "Punish"
> lore is "Issue a new punishment against {player}"
> switch to punishments when clicked

Notes menu (list, primary):
````
* * * * * * * * *
* 1 2 3 4 5 * 6 *
* * * * * * * * *
* x x x x x x x *
* * < * y * > * *
Q * * * * * * * *
````
> *, 1, 2, 3, 4, 5, 6 are the same as inspect menu. 2 is enchanted.

> x are note items (paper). Title is date and time, lore is staff name and note content (newline every 7 words). No action when clicked.

> < is a left arrow item. Title: "Previous Page". Previous page action when clicked.

> y is sign item. Title: "Create Note". Lore is "Add a new note for {player}". Opens note creation prompt in chat when clicked.

> `>` is a right arrow item. Title: "Next Page". Next page action when clicked.

Alts menu (list, primary):
````
* * * * * * * * *
* 1 2 3 4 5 * 6 *
* * * * * * * * *
* x x x x x x x *
* * < * * * > * *
Q * * * * * * * *
````
> *, 1, 2, 3, 4, 5, 6 are the same as inspect menu. 3 is enchanted.

> x are player head items for the head of each alt. lore is information about the alt (punishments, playtime, ip info, etc) and
> at the bottom "Inspect {alt}. Switch to inspect menu for {alt}." Switches to inspect menu for that alt when clicked,

> < is a left arrow item. Title: "Previous Page". Previous page action when clicked.

> `>` is a right arrow item. Title: "Next Page". Next page action when clicked.

History menu (list, primary):
````
* * * * * * * * *
* 1 2 3 4 5 * 6 *
* * * * * * * * *
* x x x x x x x *
* * < * y * > * *
Q * * * * * * * *
````
> *, 1, 2, 3, 4, 5, 6 are the same as inspect menu. 4 is enchanted.

> x are different items depending on the punishment type (ban, mute, kick). lore is information about the punishment (staff, reason, duration, date, etc) and
> "Modify punishment" at the bottom. Action when clicked, switches to punishment modification menu.

> < is a left arrow item. Title: "Previous Page". Previous page action when clicked.

> `>` is a right arrow item. Title: "Next Page". Next page action when clicked.

Modify punishment menu (secondary):
````
* * * * * * * * *
* 1 2 3 4 5 * 6 *
* * * * * * * * *
* a b c d * e f *
* * * * * * * * *
Q * * * * * * * *
````
> *, 1, 2, 3, 4, 5, 6 are the same as inspect menu. 4 is enchanted.

> a is sign item. Title: "Add Note". Lore is "Add a staff note to this punishment." Action when clicked, opens note creation prompt in chat.

> b is an arrow item. Title: "Evidence". Lore is all pieces of evidence and "Right click to dump evidence in chat, or left click to add evidence".
> Right click action is to dump evidence in chat. Left click action gives chat buttons: "[Add Evidence by Link]" & "[Add Evidence by File Upload]".
> Both are clickable, the first does a chat prompt for a link, the second sends a link to a web-dropbox (to-be added).

> c is golden apple item. Title: "Pardon Punishment". Lore is "Remove punishment and clear associated points." Action when clicked, pardons the punishment.

> d is anvil item. Title: "Change Duration". Lore is "Shorten or lengthen punishment duration." Action when clicked, opens duration change prompt in chat (ex: 30d2h3m4s, perm).

> e is empty bottle or xp bottle item depending on whether the punishment is stat-wiping (or air if it's not a ban type punishment).
> Title: "Toggle Stat-Wipe". Lore is "{enable/disable} stat-wiping for this ban." Action when clicked, toggles stat-wiping.

> f is torch or redstone torch item depending on whether the punishment is alt-blocking (or air if it's not a ban type punishment).
> Title: "Toggle Alt-Blocking". Lore is "{enable/disable} alt-blocking for this ban." Action when clicked, toggles alt-blocking.

Reports menu (list, primary):
````
* * * * * * * * *
* 1 2 3 4 5 * 6 *
* * * * * * * * *
* x x x x x x x *
* * < * y * > * *
Q * * * * * * * *
````
> *, 1, 2, 3, 4, 5, 6 are the same as inspect menu. 5 enchanted.

> x is list of reports, different items depending on report type. sorted by newest to oldest.
> Title is date and time, lore is reporter name and report content (newline every 7 words). No action when clicked.

> < is a left arrow item. Title: "Previous Page". Previous page action when clicked.

> y is an anvil item. Title: "Filter". Lore is "Filter by report type" with a list of each report type and "all" (grey for unselected,
> green for selected). Action when clicked switches through types in the list and refilters accordingly.

> `>` is a right arrow item. Title: "Next Page". Next page action when clicked.

Punish menu (primary):
````
* * * * * * * * *
* 1 2 3 4 5 * 6 *
* * * * * * * * *
* x x x x x x x *
* x x x x x x x *
Q * * * * * * * *
````
> *, 1, 2, 3, 4, 5, 6 are the same as inspect menu. 6 enchanted.

> x are configurable punishment types. clicking them opens secondary punishment menu for that type.

secondary punish menu (secondary):
````
* * * * * * * * *
* 1 2 3 4 5 * 6 *
* * * * * * * * *
* a * b * c * d *
* * * * * * e f *
Q * * * * * * * *
````
> *, 1, 2, 3, 4, 5, 6 are the same as inspect menu. 6 enchanted.

> a is lime wool item, lenient severity level item for that punishment type. Title is "Lenient", lore is offender level in category, duration, points,
> and "Click to issue {silent/public} punishment".

> b is yellow wool item, regular severity level item for that punishment type. Title is "Regular", lore is offender level in category, duration, points,
> and "Click to issue {silent/public} punishment".

> c is red wool item, aggravated severity level item for that punishment type. Title is "Aggravated", lore is offender level in category, duration, points,
> and "Click to issue {silent/public} punishment".

> d is lime or gray dye depending on whether silent mode is enabled. Title is "Silent Mode: {enabled/disabled}", lore is "Toggle silent mode for this punishment."
> Action when clicked toggles silent mode.

> e is torch or redstone torch item depending on whether alt-blocking is enabled (or air if it's not a ban type punishment).
> Title: "Alt-Blocking: {enabled/disabled}". Lore is "Toggle alt-blocking for this ban." Action when clicked, toggles alt-blocking.


> f is empty bottle or xp bottle item depending on whether stat-wiping is enabled (or air if it's not a ban type punishment).
> Title: "Stat-Wipe: {enabled/disabled}". Lore is "Toggle stat-wiping for this ban." Action when clicked, toggles stat-wiping.

Once the punishment is issued, the staff should be prompted in chat "[Add Evidence by Link]", "[Add Evidence by File Upload]", and
[Add Note], all three are clickable and hoverable, the first does a chat prompt for a link, the second sends a link to a web-dropbox (to-be added),
third also does a chat prompt for a note.

Staff GUI (not targeting a player):
-

Staff GUI parent menu (primary) (accessible with /staff):
````
* * * * * * * * *
* 1 2 3 4 * 5 6 *
* * * * * * * * *
* * * * * * * * *
Q * * * * * * * *
````
> * is blank space

> 1 is a default player head (not of any particular player). Title: "Online players"
> lore is number of online players and number of online players and "Click to view online players."
> go to list of players tab

> 2 is an endereye item. Title: "Reports"
> lore is number of unresolved reports and "Click to view unresolved reports."
> switch to reports tab when clicked

> 3 is a sword item. Title: "Recent Punishments"
> lore is "View recent punishments issued across the server."
> switch to recent punishments tab when clicked

> 4 is a paper item. Title: "Support Tickets"
> lore is "View unresolved support tickets."
> switch to support tickets tab when clicked

> 5 is a compass item. Title: "Go to panel"
> lore is "Get the link to the staff panel."
> open link in chat or open link prompt in-game (if supported)

> 6 is a command block item. Title: "Settings"
> lore is "Modify staff settings."
> switch to settings tab when clicked

Online players menu (list, primary) (staff menu):
````
* * * * * * * * *
* 1 2 3 4 * 5 6 *
* * * * * * * * *
* x x x x x x x *
* * < * y * > * *
Q * * * * * * * *
````
> *, 1, 2, 3, 4, 5, 6 are the same as staff menu. 1 is enchanted.

> x are player heads representing each player online the server.
> Title is player name, lore is time online, total playtime, # of punishments, and a list of unresolved reports against the player in past 3 hours (Date Type). 
> Click action is to open inspect menu for that player.

> < is a left arrow item. Title: "Previous Page". Previous page action when clicked.

> y is an anvil item. Title is "Sort". Right/left click cycles between "Least Playtime", "Recent Reports", "Longest Session"

> `>` is a right arrow item. Title: "Next Page". Next page action when clicked.

Reports menu (list, primary) (staff menu):
````
* * * * * * * * *
* 1 2 3 4 * 5 6 *
* * * * * * * * *
* x x x x x x x *
* * < * y * > * *
Q * * * * * * * *
````
> *, 1, 2, 3, 4, 5, 6 are the same as staff menu. 2 enchanted.

> x is list of reports, different items depending on report type, sorted by newest to oldest.
> Title is date and time, lore is reporter name and report content (newline every 7 words).
> Left click action is to open inspect menu for the reported player, right click action is to dismiss the report. 

> < is a left arrow item. Title: "Previous Page". Previous page action when clicked.

> y is an anvil item. Title: "Filter". Lore is "Filter by report type" with a list of each report type and "all" (grey for unselected,
> green for selected). Action when clicked cycles through types in the list and refilters accordingly.

> `>` is a right arrow item. Title: "Next Page". Next page action when clicked.

Recent punishments menu (list, primary) (staff menu):
````
* * * * * * * * *
* 1 2 3 4 * 5 6 *
* * * * * * * * *
* x x x x x x x *
* * < * y * > * *
Q * * * * * * * *
````
> *, 1, 2, 3, 4, 5, 6 are the same as staff menu. 3 is enchanted.

> x are different items depending on the punishment type (ban, mute, kick). lore is information about the punishment (staff, reason, duration, date, etc) and
> "Modify punishment" at the bottom. Action when clicked, switches to punishment modification menu (with staff menu as parent menu, not inspect menu).

> < is a left arrow item. Title: "Previous Page". Previous page action when clicked.

> `>` is a right arrow item. Title: "Next Page". Next page action when clicked.

Support tickets menu (list, primary) (staff menu):
````
* * * * * * * * *
* 1 2 3 4 * 5 6 *
* * * * * * * * *
* x x x x x x x *
* * < * y * > * *
Q * * * * * * * *
````
> *, 1, 2, 3, 4, 5, 6 are the same as staff menu. 4 is enchanted.

> x are book items if there have been no staff responses, or book and quill items if there have been staff responses, each representing a support ticket (newest or oldest).
> title is ticket ID. Lore is player name, title, date created, status. click action is link to ticket in staff panel in chat or link-prompt (if supported).

> y is a filter item (anvil). Title is "Filter". Lore is "Filter by ticket status" with a list of each ticket status and "all" (grey for unselected, green for selected).

> < is a left arrow item. Title: "Previous Page". Previous page action when clicked.

> `>` is a right arrow item. Title: "Next Page". Next page action when clicked.

Settings menu (list, primary) (staff menu):
````
* * * * * * * * *
* 1 2 3 4 * 5 6 *
* * * * * * * * *
* a * b c d e f *
* * * * * * * * *
Q * * * * * * * *
````
> *, 1, 2, 3, 4, 5, 6 are the same as staff menu. 6 is enchanted.

> a is an anvil item. Title: "Information". lore is your username and role, if admin permissions it has modl information like last-poll and status (healthy, degraded, unhealthy). clicking does nothing

> b is a lime or gray dye depending on whether staff notifications are enabled. Title is "Report Notifications: {enabled/disabled}", lore is "Toggle report notifications."

> c is a book item title is "Recent Ticket Updates" lore is a list of last 5 tickets they are subscribed to that have unread updates. right click cycles through (changing color

> from gray to green), left click opens the selected ticket in chat link or link prompt (if supported).

> d is only shown if the staff has admin permissions, it is a blaze rod item. Title is "Edit Roles" lore is "Modify role permissions." clicking opens role list menu.

> e is only shown if the staff has admin permissions, it is a iron chestplate item. Title is "Manage Staff" lore is "Manage staff roles." clicking opens staff list menu.

> f is only shown if the staff has admin permissions, it is a redstone item. Title is "Reload Modl" lore is "Reload messages config." clicking refreshes the locale files.

Role list menu (list, secondary) (staff menu):
````
* * * * * * * * *
* 1 2 3 4 * 5 6 *
* * * * * * * * *
* x x x x x x x *
* * < * * * > * *
Q * * * * * * * *
````
> *, 1, 2, 3, 4, 5, 6 are the same as staff menu. 6 is enchanted.

> x is a piece of paper for each role. Title is role name, lore is list of permissions. clicking opens role edit menu.

> < is a left arrow item. Title: "Previous Page". Previous page action when clicked.

> `>` is a right arrow item. Title: "Next Page". Next page action when clicked.

Role permission edit menu (list, tertiary) (staff menu):
````
* * * * * * * * *
* 1 2 3 4 * 5 6 *
* * * * * * * * *
* x x x x x x x *
* * < * * * > * *
Q * * * * * * * *
````
> *, 1, 2, 3, 4, 5, 6 are the same as staff menu. 6 is enchanted.

> x is a grey or lime dye for each permission node. Title is permission node. clicking toggles on/off for that role.

> < is a left arrow item. Title: "Previous Page". Previous page action when clicked.

> `>` is a right arrow item. Title: "Next Page". Next page action when clicked.

Staff list menu (list, secondary) (staff menu):
````
* * * * * * * * *
* 1 2 3 4 * 5 6 *
* * * * * * * * *
* x x x x x x x *
* * < * * * > * *
Q * * * * * * * *
````
> *, 1, 2, 3, 4, 5, 6 are the same as staff menu. 6 is enchanted.

> x is a player head for each staff member. Title is staff username, lore is a list of all roles with their current role bold & green and all other roles gray. 
> Right clicking cycles through roles (turning them green, not bold) and left clicking applies the selected role and make it bold.

> < is a left arrow item. Title: "Previous Page". Previous page action when clicked.

> `>` is a right arrow item. Title: "Next Page". Next page action when clicked.
