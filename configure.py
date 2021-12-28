import json
import os


class KeyCenterHelper:
    def __init__(self, key_center_file: str, key_center_template_file: str):
        self._file_name = key_center_file if key_center_file else 'key_center.json'
        self._template_file_name = key_center_template_file if key_center_template_file else 'key_center_template.json'
        print('key center file: ', self._file_name)
        print('key center file template:', self._template_file_name)

    @staticmethod
    def _get_current_path():
        return os.path.dirname(__file__)

    def _add_key_center_file_to_git_ignore(self):
        git_ignore_file = os.path.join(self._get_current_path(), '.gitignore')
        with open(git_ignore_file, 'r', encoding='utf-8') as in_file:
            ignore_line = self._file_name + "\n"
            if ignore_line not in in_file.readlines():
                with open(git_ignore_file, 'a+', encoding='utf-8') as out_file:
                    out_file.write(ignore_line)

    def prepare_key_center_file(self):
        config_obj = {}
        with open(os.path.join(self._get_current_path(), self._template_file_name), 'r', encoding='utf-8') as file:
            config_obj = json.load(file)
        for key, default_value in config_obj.items():
            try:
                config_obj[key] = type(default_value)(
                    input('Please input value for {} with type[{}]:'.format(key, type(default_value).__name__)))
            except (TypeError, ValueError) as e:
                print("Invalid value for {} with type[{}]!Please try again!".format(key, type(default_value).__name__))
                return
        # write config to real key_center config file
        with open(os.path.join(self._get_current_path(), self._file_name), 'w', encoding='utf-8') as file:
            json.dump(config_obj, file)
        self._add_key_center_file_to_git_ignore()
        print('Write config to {} succeed!'.format(self._file_name), config_obj)


if __name__ == "__main__":
    config_obj = {}
    with open(os.path.join(os.path.dirname(__file__), 'configure.json'), 'r', encoding='utf-8') as file:
        config_obj = json.load(file)
    if 'key_center' in config_obj:
        key_center_config_obj = config_obj['key_center']
        check_config_flag = input("Update key center file[Y/N]")
        if check_config_flag.lower() == 'y' or check_config_flag.lower() == 'yes':
            key_center_helper = KeyCenterHelper(key_center_config_obj.get('key_center_file', ''),
                                                key_center_config_obj.get('key_center_template_file', ''))
            key_center_helper.prepare_key_center_file()
        else:
            print("Ignore key center file update.")
    print("Configuration is complete!")
