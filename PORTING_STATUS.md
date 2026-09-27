# Impropers3DMinimap — 1.21.5 -> 1.21.11 porting status

## Fixed in this pass (verified against compiled 1.21.11 bytecode, not just mapping text)

1. **Camera.getPos() removed**
   - File: `render/simulation/SimulationRadar.java`
   - Fix: `camera.getPos()` -> `camera.getProjection().getPosition(0.0F, 0.0F)`

2. **Screen input handlers now take record types**
   - Files: `render/ui/GuiScreen.java`, `render/ui/screens/ConfigScreen.java`
   - `keyPressed(int,int,int)` -> `keyPressed(KeyInput)`
   - `keyReleased(int,int,int)` -> `keyReleased(KeyInput)`
   - `mouseClicked(double,double,int)` -> `mouseClicked(Click, boolean doubleClick)`
   - `mouseReleased(double,double,int)` -> `mouseReleased(Click)`
   - Internal element tree (`GuiElement`, `ScrollPanelElement`) is the mod's own
     hierarchy, not a Screen override, so its (double,double,int) methods were left as-is;
     GuiScreen now unpacks the record into those primitives before delegating.

3. **KeyBinding constructor now takes KeyBinding.Category, not String**
   - File: `Impropers3DMinimap.java`
   - Fix: `KeyBinding.Category.create("binds.impropers3dminimap")` used in place of the raw string.

4. **GameProfile is now a record (authlib 7.0.61)**
   - File: `util/minecraft/PlayerUtils.java`
   - Confirmed against authlib 7.0.61 sources jar: `GameProfile` changed from a class with
     getters to `record GameProfile(UUID id, String name, PropertyMap properties)`.
   - Fix: `profile.getId()` -> `profile.id()` (both call sites). Note: if you ever add
     name/properties access, those are `.name()` / `.properties()` now too.

## NOT fixed — needs more work, do not ship as-is

5. **TextRenderer constructor — real redesign, not a rename**
   - Files: `mixin/MixinFontManager.java`, `mixininterface/FontManagerAccessor.java`, `client/Impropers3DMinimapClient.java`
   - Old: `TextRenderer(Function<Identifier,FontStorage> fonts, boolean validateAdvance)`
   - New: `TextRenderer(TextRenderer.GlyphsProvider fonts)` where GlyphsProvider is
     `getGlyphs(StringVisitable source)` / `getRectangleGlyph()` — resolves per text
     source, not per Identifier. Porting `MixinFontManager.createRenderer(Identifier)`
     onto this needs a real design decision, not a mechanical swap.

6. **RenderLayer.MultiPhaseParameters / RenderPhase — confirmed gone entirely**
   - File: `util/minecraft/RenderConstants.java`
   - Replaced by a RenderPipeline-based system. Structural rewrite, not a find-replace.

7. **Matrix3x2f push/pop/peek/scale/translate — new Matrix3x2fStack API**
   - Files: `util/minecraft/RenderUtils.java`, `render/simulation/SimulationRenderer.java`
   - Not yet mapped old-call -> new-call.

Items 6-7 are the ones flagged from the start as multi-day structural work, not renames —
that hasn't changed. Items 1-4 are done and verified. Item 5 (TextRenderer) turned out to
also be a real redesign once inspected, so it's moved out of the "quick fix" bucket pending
your call on the font-resolution design question.
