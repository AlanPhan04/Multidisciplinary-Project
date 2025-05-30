import shutil
from pathlib import Path

folder_path = Path("processed_data")

def deleteFolder(print_out = True, folder_path = folder_path):
    """
    Deletes the specified folder if it exists.
    
    :param folder_path: Path to the folder to be deleted.
    """
    if folder_path.exists() and folder_path.is_dir():
        shutil.rmtree(folder_path)
        if print_out:
            print(f"Folder '{folder_path}' deleted.")
    else:
        if print_out:
            print(f"Folder '{folder_path}' does not exist.")


# deleteFolder(Path('processed_data'))
# deleteFolder(Path('logs'))