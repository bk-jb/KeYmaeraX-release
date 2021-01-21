angular.module('keymaerax.controllers').controller('ModelPlexCtrl',
    function($scope, $uibModal, $http, sessionService, spinnerService, FileSaver, Blob, modelid) {

  $scope.mxdata = {
    modelid: modelid,
    modelname: undefined,
    generatedArtifact: {
      code: undefined,
      source: undefined,
      proof: undefined
    },
    artifact: 'monitor/controller',
    additionalMonitorVars: []
  }

  $scope.getEditorMode = function(language) {
    return language.startsWith('dL') ? 'dl' : (language=='C' ? 'c_cpp' : '');
  }

  $scope.userId = sessionService.getUser();
  $scope.language = "dL"
  $scope.editorMode = $scope.getEditorMode($scope.language);
  $scope.sourceCollapsed = true;

  $scope.synthesize = function(language, monitorShape) {
    spinnerService.show('modelplexExecutionSpinner')
    $scope.language = language;
    $scope.editorMode = $scope.getEditorMode(language);
    $scope.mxdata.generatedArtifact.code = undefined;
    $scope.mxdata.generatedArtifact.source = undefined;
    $scope.mxdata.modelname = undefined;
    $http({method: 'GET',
           url: "user/" + $scope.userId + "/model/" + $scope.mxdata.modelid + "/modelplex/generate/" +
                $scope.mxdata.artifact + "/" + monitorShape + "/" + language,
           params: {vars: JSON.stringify($scope.mxdata.additionalMonitorVars)}})
      .then(function(response) {
        $scope.mxdata.generatedArtifact.code = response.data.code;
        $scope.mxdata.generatedArtifact.source = response.data.source;
        if (response.data.proof) $scope.mxdata.generatedArtifact.proof = response.data.proof;
        $scope.mxdata.modelname = response.data.modelname;
      }, function(error) {
        $uibModal.open({
          templateUrl: 'templates/modalMessageTemplate.html',
          controller: 'ModalMessageCtrl',
          size: 'md',
          resolve: {
            title: function() { return "Unable to derive monitor"; },
            message: function() { return error.data.textStatus; },
            mode: function() { return "ok"; }
          }
        });
      })
      .finally(function() { spinnerService.hide('modelplexExecutionSpinner'); });
  }

  $scope.download = function() {
    var data = new Blob([$scope.mxdata.generatedArtifact.code], { type: 'text/plain;charset=utf-8' });
    FileSaver.saveAs(data, $scope.mxdata.modelname + ($scope.language.startsWith('dL') ? '.kyx' : '.c'));
  }

  $scope.open = function(what, language, monitorShape) {
    $scope.mxdata.artifact = what;
    $scope.synthesize(language, monitorShape);
  }

  $scope.showProofArchive = function() {
    $scope.language = 'dLProof';
    $scope.editorMode = $scope.getEditorMode($scope.language);
    $scope.mxdata.generatedArtifact.code = $scope.mxdata.generatedArtifact.proof;
  }

  $scope.showProofList = function() {
    $scope.mxdata.artifact = 'proofs';
  }

  $scope.cancel = function() {
    $uibModalInstance.dismiss('cancel');
  }

});
