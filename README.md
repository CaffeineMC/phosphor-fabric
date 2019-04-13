<a href="https://www.patreon.com/bePatron?u=824442"><img src="https://i.vgy.me/AjBz9h.png" width="200"></a>

If Phosphor has made an improvement in performance for you, [please consider pledging to my Patreon!](https://patreon.com/jellysquid) Every pledge goes directly towards funding development and allows me
to continue making Minecraft mods.

-----

![Project icon](https://i.vgy.me/NBLC9k.png)

# Phosphor
![GitHub license](https://img.shields.io/github/license/jellysquid3/phosphor.svg)
![GitHub issues](https://img.shields.io/github/issues/jellysquid3/phosphor.svg)
![GitHub tag](https://img.shields.io/github/tag/jellysquid3/phosphor.svg)
[![CurseForge downloads](http://cf.way2muchnoise.eu/full_318255_downloads.svg)](https://minecraft.curseforge.com/projects/phosphor)

Phosphor is a free and open-source Minecraft mod (under GNU GPLv3) aiming to save your CPU cycles and improve performance by optimizing one of Minecraft's most inefficient areas-- the lighting engine.
It works on **both the client and server**, and can be installed on servers **without requiring clients to also have the mod**.

<p align="center">
  <img src="https://i.vgy.me/YDjLK6.png" />
</p>

_For more information about how these results were obtained, [please see my writeup on the benchmarking methodology](https://gist.github.com/jellysquid3/3b545be9c00cc59fe5c68927d03ec708)._

Phosphor is designed to be as minimal as possible in the changes it makes, and as such, does not modify the light model or interfaces of vanilla Minecraft. Because of this, Phosphor should be compatible
with many Minecraft mods (so long as they do not make drastic changes to how the lighting engine works.) If you've ran into a compatibility problem, please open an issue!

### How does it work?

Phosphor makes a variety of modifications to the vanilla lighting engine in order to improve performance. The key highlights can be found below.

- The code responsible for propagating light changes has been completely rewritten to be far more efficient than the vanilla implementation.
- Light updates are postponed until the regions they modify are queried. This allows lighting updates to be batched together more effectively and reduces the number of duplicated scheduled light updates for a block.
  This significantly reduces the CPU time spent propagating skylight updates.
- Skylight propagation on the vertical axis has been fixed to take into account incoming skylight from neighboring chunks, fixing a variety of lighting issues created during world generation and large operations
  involving large block volumes (such as /fill.)
- Chunk lighting is only performed once all adjacent chunks are loaded so sky and block light propogation is spread into neighbors correctly, preventing various visual errors.
- Through fixing various errors in vanilla's lighting engine implementation, many checks performed when relighting blocks are now skipped, reducing the overhead of lighting updates.

This list is still incomplete and a technical writeup of how Phosphor achieves such significant gains is in the works.

### License

Phosphor is licensed under GNU GPLv3, a free and open-source license. For more information, please see the [license file](https://github.com/jellysquid3/Phosphor/blob/master/LICENSE.txt).