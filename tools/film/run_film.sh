#!/bin/bash
# Showcase film: dedicated server + caster + rival + camera client, camera screen recorded with ffmpeg.
P=/home/user/Hexmagic
L=${FILM_OUT:-/tmp/hexfilm}; mkdir -p $L
cd $P
pkill -f Dfml.modFolders; pkill Xvfb; pkill ffmpeg; sleep 3
rm -rf run-server/world run-hexmage/screenshots run-rival/screenshots run-cam/screenshots
cat > run-server/server.properties <<'PROPS'
online-mode=false
level-type=minecraft\:flat
spawn-protection=0
difficulty=easy
generate-structures=false
view-distance=6
simulation-distance=6
server-port=25565
pvp=true
allow-flight=true
enforce-secure-profile=false
max-tick-time=-1
PROPS
for who in hexmage rival cam; do
  mkdir -p run-$who
  rd=3; fps=20; [ $who = cam ] && { rd=6; fps=120; }
  cat > run-$who/options.txt <<OPTS
onboardAccessibility:false
tutorialStep:none
joinedFirstServer:true
skipMultiplayerWarning:true
renderDistance:$rd
simulationDistance:5
maxFps:$fps
enableVsync:false
renderClouds:"false"
chatVisibility:0
pauseOnLostFocus:false
narrator:0
soundCategory_master:0.0
OPTS
done
export LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe MESA_GL_VERSION_OVERRIDE=4.5
Xvfb :97 -screen 0 1280x720x24 -nolisten tcp > $L/xvfb97.log 2>&1 &
Xvfb :98 -screen 0 640x360x24 -nolisten tcp > $L/xvfb98.log 2>&1 &
Xvfb :99 -screen 0 640x360x24 -nolisten tcp > $L/xvfb99.log 2>&1 &
sleep 2
./gradlew runServer -PhexDirector --console=plain > $L/server.log 2>&1 &
n=0; until grep -a -q -E 'Done \(' $L/server.log; do sleep 2; n=$((n+1)); { grep -a -q "BUILD FAILED" $L/server.log || [ $n -gt 150 ]; } && { echo SERVER_FAILED; pkill -f Dfml.modFolders; exit 1; }; done
echo "server up $(date +%T)"
LP_NUM_THREADS=1 DISPLAY=:99 ./gradlew runClient -PhexDemo=film -PhexUser=Rival -PhexSize=480x270 -PhexServer=localhost:25565 --console=plain > $L/rival.log 2>&1 &
sleep 25
LP_NUM_THREADS=1 DISPLAY=:98 ./gradlew runClient -PhexDemo=film -PhexUser=HexMage -PhexSize=480x270 -PhexServer=localhost:25565 --console=plain > $L/mage.log 2>&1 &
sleep 25
LP_NUM_THREADS=3 DISPLAY=:97 ./gradlew runClient -PhexDemo=film -PhexUser=Cam -PhexSize=1280x720 -PhexServer=localhost:25565 --console=plain > $L/cam.log 2>&1 &
CAMPID=$!
n=0; until grep -a "Cam\[" $L/server.log | grep -q "logged in"; do sleep 2; n=$((n+1)); [ $n -gt 200 ] && { echo CAM_NEVER_JOINED; pkill -f Dfml.modFolders; exit 1; }; done
echo "cam joined $(date +%T)"
date +%s%3N > $L/ff_start_ms
ffmpeg -y -loglevel error -f x11grab -video_size 1280x720 -framerate 20 -i :97.0 -c:v libx264 -preset ultrafast -qp 18 -pix_fmt yuv420p $L/raw.mkv > $L/ffmpeg.log 2>&1 &
FFPID=$!
( sleep 1500; echo CAM_TIMEOUT; pkill -f Dfml.modFolders ) &
WD=$!
wait $CAMPID
kill $WD 2>/dev/null
echo "cam exited $(date +%T)"
kill -INT $FFPID; sleep 3
pkill -f Dfml.modFolders; sleep 3; pkill Xvfb
echo FILM_DONE
