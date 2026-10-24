CAR SPRITES (the 4 files here are placeholder art - overwrite them with yours, same file names)

  car_rally.png   -> population 1
  car_sports.png  -> population 2
  car_taxi.png    -> population 3
  car_truck.png   -> population 4   (population 5+ reuses these in order)

Rules
  - PNG with transparent background
  - car faces RIGHT (nose toward the right edge of the image)
  - about 2:1 wide, e.g. 128x64 (anything up to 1280 px is fine; the game draws it 44 units wide)
  - top-down view, car filling the image edge to edge (no empty margin)
  - lowercase file names exactly as above

OPTIONAL
  menu_bg.png      full-screen menu background (stretched)
  btn_<id>.png     button skins, ids: start resume creator editor load settings
                   pause s0 s1 s2 s3 follow free map net resumesim tune psettings export exit
                   ebk eundo eclr edel esave eload      (text is drawn on top)

After changing PNGs: rebuild/run. (Settings > Sprite Manager shows [OK]/[--] per file.)
