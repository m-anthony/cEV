# cEV Calculator for Betclic

Compute your cEV (Chip Expected Value) for Betclic, filtered by buy-in and/or position.

## Why this project?

The main goal of this project was to experiment with **Kotlin** while building something useful for the poker community.
In many French poker Discord communities, beginners starting at the lowest stakes often want to track their performance
but are reluctant to invest in a commercial tracker.

Currently, the tool only supports **Betclic.fr**, but I plan to implement additional poker rooms soon (prioritizing the
French market).

**Why Betclic?**
* **Personal use:** It is my primary poker room, so I have plenty of Hand History (HH) files for testing.
* **Popularity:** It is one of the largest French rooms and is highly recommended for its attractive multiplier distribution.
* **No HUDs:** Since HUDs are prohibited on Betclic, purchasing a full tracker is often seen as less of a priority for players compared to other sites.

## Installation for End Users

* **Java Requirements:** You must have **Java 21 or higher** installed. You can download it for free from [Adoptium (Temurin)](https://adoptium.net/fr/temurin/releases) or any JDK vendor of your choice.
* **Download:** Get the ZIP file from the [latest release](https://github.com/m-anthony/cEV/releases).
* **Setup:**
    1. Unzip the archive.
    2. Edit the `cev.config.txt` file in the `bin` folder to point the software to your Hand History directory.
    3. Run `cev.bat` (Windows) or `./cev` (macOS / Linux).
    4. Specify the file you want to analyze, or leave it empty to scan the entire folder.

## How to Build

When cloning the project, ensure you use the `--recursive` flag because it includes a Git submodule:
`git clone --recursive [URL]`

If you have already cloned the repository without this flag, you can fix it by running:
`git submodule update --init --recursive`

Once ready, a standard `./gradlew build` will work. Note that the first build may take a few minutes as it generates the preflop equity tables.

## How It Works

The parser extracts the actual chip results for **Hero** from each hand. However, when an all-in occurs before the river, the actual result is replaced by Hero's **Equity** (Expected Value).
* The **cEV** of a tournament is the sum of the cEV of all hands played.
* The average cEV across all tournaments is a key performance indicator, which can also be used for variance simulations in tools like **SwongSim**.

### Performance Optimizations

To compute equity efficiently, the tool uses the [SKPokerEval](https://github.com/kennethshackleton/SKPokerEval) algorithm. I created a Java-based fork of this algorithm with a more efficient hash table (included as a submodule).

* **Postflop Equity:** The tool simulates every possible turn and/or river card and runs SKPokerEval. With a maximum of 990 possible combinations, this is extremely fast.
* **Preflop Equity:** This is more complex. A 2-way preflop matchup involves over 1.7 million possible boards.
    * **Heads-up (2-way):** Equities are precomputed during the build and the cache is loaded at startup for near-instant results.
    * **3-way:** These are approximated using **Monte-Carlo simulations**.

Even though 3-way all-ins are rarer, they are the most CPU-intensive part of the process. To maintain speed, these calculations are performed in parallel using **Kotlin Coroutines**.

**Result:** These optimizations allow the tool to process 22k Spin Hand Histories in less than 5 seconds on a Core i5-11400F.

## Preflop Equity Cache & Canonical Hand Pairs

Caching every possible hand matchup would lead to $1,624,350$ possibilities. Fortunately, through symmetry, this can be greatly reduced:
* There are only **169 hand classes** (AA, ATs, 97o, etc.).
* By applying ordering rules and suit symmetries (e.g., in ATo vs KQs, only 3 suit combinations actually change the equity), the cache is reduced to **58,630 canonical hand pairs**.

This reduced set is small enough to fit in memory and can be precomputed in under 5 minutes on a modern CPU. Note that end users do not need to do this, as the cache is included in the release package.

## Why does my cEV differ from other tools?

If you notice differences between this tool and another (such as PokerTracker 4 or Hand2Note), keep in mind that cEV is
a metric that requires a large sample size of tournaments to be meaningful. On such representative samples, 
any discrepancies should be negligible.

The most common root causes for these differences include:
- **Equity Calculation Methods**: This tool uses exact equity calculations for most scenarios but relies on Monte-Carlo approximations for 3-way pots. Other tools may use different iteration counts or different algorithms entirely.
- **Side Pot Logic**: I have implemented the same logic as PokerTracker 4: equity-based cEV is only calculated if every active player is all-in on the same street. If action continues in a side pot, the actual chip result is used for the main pot instead.
- **Software Bugs**: While cross-referencing my results with PT4, I actually discovered a bug in their calculation engine and reported it to their support team. If you believe you have found a calculation error for a specific hand in this tool, please open an issue with the hand history attached.