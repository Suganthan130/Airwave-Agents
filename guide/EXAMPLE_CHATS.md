# Airwave Radio — Example Listener Chats & Test Prompts

A reference handbook of sample listener chat messages for testing and demoing all interactive AI Co-Host features on **Airwave Radio**.

Use these messages in the Listener Web App chat (`https://airwave-agents.web.app/station/<uid>`) or directly in test scripts to trigger AI RJ responses on-air.

---

## Testing Accounts

| Role | Email | Password | Details |
|---|---|---|---|
| **Broadcaster (Android)** | `test@radiostreamhub.com` | `Test@147!` | Pre-configured with live Icecast stream, settings, and library. |
| **Listener (Web)** | `listener@radiostreamhub.com` | `PublicTest@147!` | Pre-configured with UK profile (`United Kingdom` 🇬🇧). |

---

## 1. Song Requests — Songs IN Playlist (Move to Play Next + RJ Dedication)

When a listener requests any of the pre-loaded songs below, Gemini detects the request, verifies that the track is in the library, has the AI RJ introduce the song with a personal dedication on-air, and automatically moves the track to play next!

### Pre-loaded Library Tracks in Airwave Radio:
- **Across the Oceans**
- **Athirapalli**
- **Dancing in the Stars**
- **Eternal Light**
- **Summer Adventures**
- **Summer Vibes**
- **Original Song**
- **Vocal Song 1 / 2 / 3**

### Example Messages (English)
| Listener Name | Chat Message | Expected On-Air Response & Action |
|---|---|---|
| **Sarah (UK)** | `Can you please play Across the Oceans next? Loving the vibe today!` | RJ confirms track is in library, gives Sarah an on-air shoutout, and **Across the Oceans** moves to Up Next immediately after the shoutout clip. |
| **Michael (USA)** | `Please play Dancing in the Stars! It's one of my favorites.` | RJ acknowledges Michael, dedicates "Dancing in the Stars", and queues the track to play next. |
| **David (Australia)** | `Could you put Summer Vibes? Feeling energetic here in Sydney!` | RJ sends love to Sydney, announces "Summer Vibes" is lined up next. |
| **Elena (Canada)** | `Play Athirapalli please RJ!` | RJ confirms "Athirapalli" was found in library and moves it up next. |
| **Alex (Singapore)** | `Can we hear Eternal Light? Such a peaceful song.` | RJ warmly dedicates "Eternal Light" to Alex. |

### Example Messages (Tamil)
| Listener Name | Chat Message | Expected On-Air Response & Action |
|---|---|---|
| **Karthik (Chennai)** | `Across the Oceans paattu podunga RJ, romba pidikkum!` | RJ in Tamil confirms the song is in the station library and says *"உடனே play ஆகுது!"*, moving the track to play next. |
| **Nandhini (Jaffna)** | `Athirapalli song play pannunga please!` | RJ announces the request on-air in Tamil and schedules Athirapalli right after the current song. |
| **Suresh (Malaysia)** | `Summer Vibes paattu varuma RJ? Request please.` | RJ greets Suresh from Malaysia and confirms the song is queued for him. |

---

## 2. Song Requests — Songs NOT in Playlist (Polite Apology + Tomorrow Promise)

When a listener requests a song that is **not** currently in the station library, Gemini recognizes the request, greets the listener with warmth, thanks them, politely explains the song is not in the library right now, and promises enthusiastically to add it tomorrow.

### Example Messages (English)
| Listener Name | Chat Message | Expected On-Air Response & Action |
|---|---|---|
| **Emma (London)** | `Can you play Shape of You by Ed Sheeran please?` | RJ thanks Emma warmly: *"Hey Emma, thanks so much for tuning in! 'Shape of You' isn't in our station library right now, but I promise I'll add it tomorrow just for you! Stay tuned for more great tracks!"* |
| **Daniel (New York)** | `Please play Despacito, let's turn the party up!` | RJ thanks Daniel, politely notes it's not currently stocked, and promises to have it in the library tomorrow. |
| **Chloe (Dublin)** | `Could you please play Bohemian Rhapsody by Queen?` | RJ thanks Chloe for the classic rock request and promises enthusiastically to add Queen to tomorrow's playlist. |
| **Liam (Auckland)** | `Play Believer by Imagine Dragons please RJ!` | RJ acknowledges Liam from New Zealand, thanks him for listening, and promises to add it tomorrow. |

### Example Messages (Tamil)
| Listener Name | Chat Message | Expected On-Air Response & Action |
|---|---|---|
| **Anbu (Coimbatore)** | `Vaathi Coming paattu podunga RJ please!` | RJ in Tamil responds warmly: *"ஹலோ அன்பு! Request-க்கு ரொம்ப நன்றி. ஆனா 'Vaathi Coming' இப்போ நம்ம library-ல இல்ல, நாளைக்கு கண்டிப்பா add பண்றேன்! தொடர்ந்து கேளுங்க Airwave Radio!"* |
| **Priya (Colombo)** | `Illuminati song request please RJ!` | RJ thanks Priya, explains the song isn't in the library today, and promises to add it tomorrow. |
| **Ramesh (Singapore)** | `Arabic Kuthu paattu podunga RJ!` | RJ greets Ramesh, thanks him politely, and confirms it will be added tomorrow. |

---

## 3. Station Time Check & Now-Playing Inquiries

Listeners asking for the time or what is currently playing trigger informative and natural RJ announcements.

### Example Messages (English)
| Listener Name | Chat Message | Expected On-Air Response |
|---|---|---|
| **Lucas (Germany)** | `Hey RJ, what time is it right now?` | RJ announces the current time in a lively radio tone, along with the now-playing track. |
| **Jessica (USA)** | `Time check please RJ! Time is flying by with this music.` | RJ gives the live clock check with radio flair and thanks Jessica. |
| **Mark (UAE)** | `What track is playing right now? Sounds fantastic.` | RJ tells Mark the exact track title and artist currently broadcasting on-air. |

### Example Messages (Tamil)
| Listener Name | Chat Message | Expected On-Air Response |
|---|---|---|
| **Muthu (Madurai)** | `RJ ippo time enna aaguthu?` | RJ delivers a crisp time check in Tamil with the station identity. |
| **Kavitha (Batticaloa)** | `Time sollunga RJ, enna song ippo poguthu?` | RJ tells the time and gives details on the current on-air track. |

---

## 4. Listener Shoutouts, Celebrations & Dedications

Airwave RJ acts as an empathetic co-host celebrating life events and greeting listeners worldwide.

### Example Messages (English)
| Listener Name | Chat Message | Expected On-Air Response |
|---|---|---|
| **Maya (Chicago)** | `It's my best friend Kevin's 25th birthday today! Can you give him a big birthday shoutout?` | RJ brings high celebration energy, wishing Kevin a happy 25th birthday and dedicating the ongoing broadcast to him. |
| **Oliver (London)** | `Listening from a rainy evening in London. The music is keeping my spirits warm!` | RJ sends warmth to London, jokes about the rain, and creates a cozy radio moment. |
| **Sanjay (Dubai)** | `Driving home after a long workday. Thanks for keeping me company on the road!` | RJ encourages safe driving, thanks Sanjay for tuning in, and plays uplifting tunes. |

### Example Messages (Tamil)
| Listener Name | Chat Message | Expected On-Air Response |
|---|---|---|
| **Dinesh (Chennai)** | `En friend Sangeetha-vukku birthday wish sollunga RJ!` | RJ delivers an energetic Tamil birthday greeting filled with celebration and good wishes. |
| **Meena (Trincomalee)** | `Kudumbathoda radio kekkurom RJ, ellarukkum oru hi sollunga!` | RJ warmly greets the whole family tuning in together. |

---

## 5. RJ Banter, Chit-Chat & Humor

Listener messages that invite the AI RJ to share stories, jokes, or station vibes.

### Example Messages (English)
| Listener Name | Chat Message | Expected On-Air Response |
|---|---|---|
| **Sophie (Sydney)** | `RJ, tell us a quick radio joke to make us laugh!` | RJ delivers a clean, punchy, radio-themed joke with laughter and punchline timing. |
| **Ethan (Austin)** | `Who is your favorite artist to play on Airwave Radio?` | RJ shares an enthusiastic radio co-host opinion about favorite music styles and station hits. |
| **Zoe (Seattle)** | `Your voice sounds super smooth! Keep rocking the airwaves!` | RJ thanks Zoe with genuine appreciation and shares the love with the audience. |

### Example Messages (Tamil)
| Listener Name | Chat Message | Expected On-Air Response |
|---|---|---|
| **Vijay (Trichy)** | `RJ oru chinna comedy joke sollunga kekkalam!` | RJ delivers a lighthearted Tamil joke with radio charisma. |
| **Aishwarya (Salem)** | `Unga voice romba nallaa irukku RJ! Keep it up!` | RJ thanks Aishwarya warmly in colloquial Tamil. |

---

## 6. Safety & 18+ Content Filter Test (Rejection Testing)

Airwave Radio enforces an AI safety filter. Messages containing inappropriate, explicit, abusive, or offensive content are automatically filtered out.

| Test Input | Expected System Behavior |
|---|---|
| Inappropriate, abusive, or vulgar messages | Filtered by Gemini safety instructions (`replyText` set to neutral polite response or flagged as unsafe). Not broadcasted on-air. |
| Spam or random character flooding (`asdfghjkl12345`) | Ignored or answered with a friendly generic greeting without breaking live audio. |
