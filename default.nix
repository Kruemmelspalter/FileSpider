let
    pkgs = import <nixpkgs> {};
in
{
    filespider = pkgs.callPackage ./filespider.nix {};
}