![Project icon](https://git-assets.jellysquid.me/hotlink-ok/phosphor/icon-rounded-128px.png)

# Phosphor (for Fabric)
![GitHub license](https://img.shields.io/github/license/jellysquid3/phosphor-fabric.svg)
![GitHub issues](https://img.shields.io/github/issues/jellysquid3/phosphor-fabric.svg)
![GitHub tag](https://img.shields.io/github/tag/jellysquid3/phosphor-fabric.svg)

Phosphor is a free and open-source Minecraft mod (under GNU GPLv3) aiming to save your CPU cycles and improve performance by optimizing one of Minecraft's most inefficient areas-- the lighting engine.
It works on **both the client and server**, and can be installed on servers **without requiring clients to also have the mod**.

The mod is designed to be as minimal as possible in the changes it makes, and as such, does not modify the light model or interfaces of vanilla Minecraft. Because of this, Phosphor should be compatible
with many Minecraft mods (so long as they do not make drastic changes to how the lighting engine works.) If you've ran into a compatibility problem, please open an issue!


---

## Installation

### Manual installation (recommended)

You will need Fabric Loader 0.10.x or newer installed in your game in order to load Phosphor. If you haven't installed
Fabric mods before, you can find a variety of community guides for doing so [here](https://fabricmc.net/wiki/install).

#### Stable releases

![GitHub release](https://img.shields.io/github/release/CaffeineMC/phosphor-fabric.svg)

The latest releases of Phosphor are published to our [Modrinth](https://modrinth.com/mods/phosphor) and
[GitHub release](https://github.com/CaffeineMC/phosphor-fabric/releases) pages. Releases are considered by our team to be
**suitable for general use**, but they are not guaranteed to be free of bugs and other issues.

Usually, releases will be made available on GitHub slightly sooner than other locations.

#### Bleeding-edge builds (unstable)

[![GitHub build status](https://img.shields.io/github/workflow/status/CaffeineMC/phosphor-fabric/Java CI with Gradle/1.16.x/dev)](https://github.com/CaffeineMC/phosphor-fabric/actions/workflows/gradle.yml)

If you are a player who is looking to get your hands on the latest **bleeding-edge changes for testing**, consider
taking a look at the automated builds produced through our [GitHub Actions workflow](https://github.com/CaffeineMC/phosphor-fabric/actions/workflows/gradle.yml?query=event%3Apush).
This workflow automatically runs every time a change is pushed to the repository, and as such, the builds it produces
will generally reflect the latest snapshot of development.

Bleeding edge builds will often include unfinished code that hasn't been extensively tested. That code may introduce
incomplete features, bugs, crashes, and all other kinds of weird issues. You **should not use these bleeding edge builds**
unless you know what you are doing and are comfortable with software debugging. If you report issues using these builds,
we will expect that this is the case. Caveat emptor.

### CurseForge

[![CurseForge downloads](http://cf.way2muchnoise.eu/full_394468_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/phosphor)

If you are using the CurseForge client, you can continue to find downloads through our
[CurseForge page](https://www.curseforge.com/minecraft/mc-mods/phosphor). Unless you are using the CurseForge
client, you should prefer the downloads linked on our Modrinth or GitHub release pages above.

The CurseForge client does not natively support Fabric modding, so you will need to install
[Jumploader](https://www.curseforge.com/minecraft/mc-mods/jumploader) in order to set up your Fabric environment. Due to
the extra complexity and startup overhead this workaround adds, we generally do not recommend using this method unless
you have an existing setup with it.

---

### Reporting Issues

You can report bugs and crashes by opening an issue on our [issue tracker](https://github.com/CaffeineMC/phosphor-fabric/issues).
Before opening a new issue, use the search tool to make sure that your issue has not already been reported and ensure
that you have completely filled out the issue template. Issues which are duplicates or do not contain the necessary
information to triage and debug may be closed. 

Please note that while the issue tracker is open to feature requests, development is primarily focused on
improving hardware compatibility, performance, and finishing any unimplemented features necessary for parity with
the vanilla renderer.

### Community
[![Discord chat](https://img.shields.io/badge/chat%20on-discord-7289DA?logo=discord&logoColor=white)](https://jellysquid.me/discord)

We have an [official Discord community](https://jellysquid.me/discord) for all of our projects. By joining, you can:
- Get installation help and technical support with all of our mods 
- Be notified of the latest developments as they happen
- Get involved and collaborate with the rest of our team
- ... and just hang out with the rest of our community.

---

### Building from sources

Support is not provided for setting up build environments or compiling the mod. We ask that
users who are looking to get their hands dirty with the code have a basic understanding of compiling Java/Gradle
projects. The basic overview is provided here for those familiar.

#### Requirements

- JRE 8 or newer (for running Gradle)
- JDK 8 (optional)
  - If you neither have JDK 8 available on your shell's path or installed through a supported package manager (such as
[SDKMAN](https://sdkman.io)), Gradle will automatically download a suitable toolchain from the [AdoptOpenJDK project](https://adoptopenjdk.net/)
and use it to compile the project. For more information on what package managers are supported and how you can
customize this behavior on a system-wide level, please see [Gradle's Toolchain user guide](https://docs.gradle.org/current/userguide/toolchains.html).
- Gradle 6.7 or newer (optional)
  - The [Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:using_wrapper) is provided in
    this repository can be used instead of installing a suitable version of Gradle yourself. However, if you are building
    many projects, you may prefer to install it yourself through a suitable package manager as to save disk space and to
    avoid many different Gradle daemons sitting around in memory.

#### Building with Gradle

Phosphor ses a typical Gradle project structure and can be built by simply running the default `build` task. After Gradle
finishes building the project, you can find the build artifacts (typical mod binaries, and their sources) in
`build/libs`.

**Tip:** If this is a one-off build, and you would prefer the Gradle daemon does not stick around in memory afterwards,
try adding the [`--no-daemon` flag](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:disabling_the_daemon)
to ensure that the daemon is torn down after the build is complete. However, subsequent builds of the project will
[start more slowly](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:why_the_daemon) if the Gradle
daemon is not available to be re-used.


Build artifacts ending in `dev` are outputs containing the sources and compiled classes
before they are remapped into stable intermediary names. If you are working in a developer environment and would
like to add the mod to your game, you should prefer to use the `modRuntime` or `modCompile` configurations provided by
Loom instead of these outputs.

### License

Phosphor is licensed under GNU LGPLv3, a free and open-source license. For more information, please see the [license file](https://github.com/jellysquid3/phosphor-fabric/blob/1.16.x/dev/LICENSE.txt).
