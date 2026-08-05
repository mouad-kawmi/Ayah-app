# Contributing to Ayah

First off, thank you for considering contributing to Ayah! It's people like you that make Ayah such a great tool for the community.

## Where do I go from here?

If you've noticed a bug or have a feature request, make one! It's generally best if you get confirmation of your bug or approval for your feature request this way before starting to code.

## Fork & create a branch

If this is something you think you can fix, then fork Ayah and create a branch with a descriptive name.

## Get the test suite running

Make sure your changes don't break the existing codebase. We recommend running `./gradlew build` before submitting your PR.

## Implement your fix or feature

At this point, you're ready to make your changes! Feel free to ask for help; everyone is a beginner at first.

## Make a Pull Request

At this point, you should switch back to your master branch and make sure it's up to date with Ayah's master branch:

```sh
git remote add upstream https://github.com/mouad-kawmi/Ayah-app.git
git checkout main
git pull upstream main
```

Then update your feature branch from your local copy of master, and push it!

```sh
git checkout 325-add-my-feature
git rebase main
git push --set-upstream origin 325-add-my-feature
```

Finally, go to GitHub and make a Pull Request.

## Keeping your Pull Request updated

If a maintainer asks you to "rebase" your PR, they're saying that a lot of code has changed, and that you need to update your branch so it's easier to merge.
