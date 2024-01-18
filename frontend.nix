{mkYarnPackage, fetchYarnDeps}:

source:
mkYarnPackage rec {
  src = source;

  offlineCache = fetchYarnDeps {
    yarnLock = src + "/yarn.lock";
    sha256 = "sha256-ts5WW422nUboTRp29jZ58gx+5aIPcPz/jjzR3Wo5zZU=";
  };

  packageJSON =  src + "/package.json";
  buildPhase = ''
    export HOME=$(mktemp -d)
    yarn run --offline build
    cp -r deps/filespider/dist $out
  '';
  distPhase = "true";
  dontInstall = true;

}