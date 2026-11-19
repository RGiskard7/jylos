{
  description = "Jylos: local-first Markdown notes";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
    in
    {
      packages.${system} = {
        jylos = pkgs.callPackage ./jylos.nix { };
        default = self.packages.${system}.jylos;
      };
      apps.${system}.default = {
        type = "app";
        program = "${self.packages.${system}.jylos}/bin/jylos";
        meta.description = "Run Jylos";
      };
    };
}
