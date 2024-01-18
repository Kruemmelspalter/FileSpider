{ callPackage, fetchFromGitHub, makeRustPlatform, lib, stdenv }:

{ date, channel }:

let mozillaOverlay = fetchFromGitHub {
      owner = "mozilla";
      repo = "nixpkgs-mozilla";
      rev = "9b11a87c0cc54e308fa83aac5b4ee1816d5418a2";
      sha256 = "sha256-+gi59LRWRQmwROrmE1E2b3mtocwueCQqZ60CwLG+gbg=";
    };
    mozilla = callPackage "${mozillaOverlay.out}/package-set.nix" {};
    rustSpecific = (mozilla.rustChannelOf { inherit date channel; }).rust;

in makeRustPlatform {
  cargo = rustSpecific;
  rustc = rustSpecific;
}